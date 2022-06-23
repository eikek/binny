package binny.pglo.impl

import java.security.MessageDigest
import javax.sql.DataSource

import binny._
import binny.jdbc.impl.Implicits._
import binny.jdbc.impl.{DbRun, DbRunApi}
import binny.util.Logger
import cats.data.OptionT
import cats.effect._
import cats.implicits._
import fs2.{Chunk, Stream}
import org.postgresql.PGConnection
import org.postgresql.largeobject.{LargeObject, LargeObjectManager}
import scodec.bits.ByteVector

final class PgApi[F[_]: Sync](table: String, logger: Logger[F]) {
  implicit private val log: Logger[F] = logger

  def createTable: DbRun[F, Int] =
    DbRun.executeUpdate(
      s"""
         |CREATE TABLE IF NOT EXISTS "$table" (
         |  "file_id" varchar(254) NOT NULL PRIMARY KEY,
         |  "data_oid" oid NOT NULL
         |);
         |""".stripMargin
    )

  def listAllIds(
      prefix: Option[String],
      chunkSize: Int,
      ds: DataSource
  ): Stream[F, BinaryId] = {
    val dbapi = new DbRunApi[F](table, logger)
    dbapi.listAllIds(prefix, chunkSize, ds)
  }

  def loManager: DbRun[F, LargeObjectManager] =
    DbRun.delay(_.unwrap(classOf[PGConnection]).getLargeObjectAPI)

  def loManagerS: Stream[DbRun[F, *], LargeObjectManager] =
    Stream.eval(loManager)

  def createLO(lom: LargeObjectManager): DbRun[F, Long] =
    DbRun.delay(_ => lom.createLO(LargeObjectManager.READWRITE))

  def openLOWrite(): DbRun[Resource[F, *], LargeObject] =
    for {
      _ <- DbRun.makeTX
      lom <- loManager.mapF(Resource.eval)
      oid <- createLO(lom).mapF(Resource.eval)
      _ <- DbRun(_ => Resource.eval(logger.trace(s"Open lo $oid for writing")))
      lo <- DbRun.resource(_ => lom.open(oid))(_.close())
    } yield lo

  def openLORead(lom: LargeObjectManager, oid: Long): DbRun[Resource[F, *], LargeObject] =
    for {
      _ <- DbRun.makeTX
      _ <- DbRun(_ => Resource.eval(logger.trace(s"Open lo $oid for reading")))
      lo <- DbRun.resource(_ => lom.open(oid, LargeObjectManager.READWRITE))(_.close())
    } yield lo

  def findOid(id: BinaryId): DbRun[F, Option[Long]] =
    DbRun
      .query[F](s"""SELECT data_oid FROM "$table" WHERE "file_id" = ?""")(
        _.setString(1, id.id)
      )
      .use(DbRun.readOpt[F, Long](_.getLong(1)))

  def exists(id: BinaryId): DbRun[OptionT[F, *], Unit] =
    DbRun
      .query[F](s"""SELECT file_id FROM "$table" WHERE "file_id" = ? LIMIT ?""") { stmt =>
        stmt.setString(1, id.id)
        stmt.setInt(2, 1)
      }
      .use(DbRun.readOpt[F, Unit](_ => ()))
      .mapF(OptionT.apply)

  def insert(id: BinaryId, bytes: Stream[F, Chunk[Byte]]): DbRun[F, Int] = {
    val copy: DbRun[F, (Long, Int)] =
      for {
        res <- openLOWrite().use(obj =>
          bytes
            .evalMap(chunk =>
              Sync[F]
                .blocking {
                  val bs = chunk.toArraySlice
                  obj.write(bs.values, bs.offset, bs.size)
                  chunk.size
                }
            )
            .compile
            .foldMonoid
            .map(size => (obj.getLongOID, size))
        )
      } yield res

    for {
      n <- copy
      _ <- DbRun(_ => logger.debug(s"Inserted large object ${n._1} of size ${n._2}"))
      _ <- DbRun.update(
        s"""INSERT INTO "$table" ("file_id", "data_oid") VALUES (?,?)"""
      ) { ps =>
        ps.setString(1, id.id)
        ps.setLong(2, n._1)
      }
    } yield n._2
  }

  def delete(id: BinaryId): DbRun[F, Int] =
    for {
      oid <- findOid(id)
      n <- DbRun.update[F](s"""DELETE FROM "$table" WHERE "file_id" = ?""")(
        _.setString(1, id.id)
      )
      lom <- loManager
      _ <- DbRun.delay(_ => oid.foreach(id => lom.delete(id)))
    } yield n

  def computeAttr(
      id: BinaryId,
      detect: ContentTypeDetect,
      hint: Hint,
      chunkSize: Int
  ): DbRun[F, BinaryAttributes] =
    for {
      lom <- loManager
      oid <- findOid(id)
      attr <- openLORead(lom, oid.getOrElse(-1)).use(obj =>
        Sync[F].blocking {
          val buffer = new Array[Byte](chunkSize)
          var len = 0L
          val md = MessageDigest.getInstance("SHA-256")
          var ct = None: Option[SimpleContentType]

          var read: Int = -1
          while ({ read = obj.read(buffer, 0, buffer.length); read } > 0) {
            md.update(buffer, 0, read)
            len = len + read
            if (ct.isEmpty) {
              ct = Some(detect.detect(ByteVector.view(buffer, 0, read), hint))
            }
          }
          BinaryAttributes(
            ByteVector.view(md.digest()),
            ct.getOrElse(SimpleContentType.octetStream),
            len
          )
        }
      )
    } yield attr

  def computeLength(id: BinaryId): DbRun[F, Long] =
    for {
      lom <- loManager
      oid <- findOid(id)
      attr <- openLORead(lom, oid.getOrElse(-1)).use(obj =>
        Sync[F].blocking {
          obj.size64()
        }
      )
    } yield attr

  def detectContentType(
      id: BinaryId,
      detect: ContentTypeDetect,
      hint: Hint
  ): DbRun[F, SimpleContentType] =
    for {
      chunk <- loadChunk(id, ByteRange.Chunk(0, 50))
      ct = detect.detect(chunk.toByteVector, hint)
    } yield ct

  def loadChunkByOID(oid: Long, range: ByteRange.Chunk): DbRun[F, Chunk[Byte]] = {
    def readChunk(obj: LargeObject): F[Chunk[Byte]] =
      Sync[F].blocking {
        obj.seek64(range.offset, LargeObject.SEEK_SET)
        val buf = obj.read(range.length)
        Chunk.array(buf)
      }

    def load(lom: LargeObjectManager): DbRun[F, Chunk[Byte]] =
      openLORead(lom, oid)
        .use(readChunk)
        .flatTap(ch => DbRun(_ => logger.trace(s"Got chunk from db: ${ch.size}")))

    for {
      lom <- loManager
      res <- load(lom)
    } yield res
  }

  def loadChunk(
      id: BinaryId,
      range: ByteRange.Chunk
  ): DbRun[F, Chunk[Byte]] =
    for {
      _ <- DbRun(_ => logger.debug(s"Load ${id.id} single chunk $range"))
      oid <- findOid(id)
      res <- oid match {
        case Some(id) => loadChunkByOID(id, range)
        case None     => DbRun.pure[F, Chunk[Byte]](Chunk.empty)
      }
    } yield res

  def loadAll(
      id: BinaryId,
      range: ByteRange,
      chunkSize: Int
  ): DbRun[Stream[F, *], Byte] = {
    val offset = range.fold(0L, _.offset)
    val targetLength = range.fold(None, _.length.some)
    def setLimits(obj: LargeObject): F[Unit] =
      range match {
        case ByteRange.All =>
          ().pure[F]
        case ByteRange.Chunk(offset, _) =>
          Sync[F].blocking(obj.seek64(offset, LargeObject.SEEK_SET))
      }

    def readChunk(obj: LargeObject, len: Int): F[Chunk[Byte]] =
      Sync[F].blocking(Chunk.array(obj.read(len)))

    def readAll(obj: LargeObject): Stream[F, Byte] =
      Stream
        .eval(Sync[F].blocking(obj.tell64()).map(pos => pos - offset))
        .flatMap { bytesRead =>
          val rest = math.min(
            chunkSize,
            targetLength.map(tl => (tl - bytesRead).toInt).getOrElse(chunkSize)
          )
          if (targetLength.exists(_ <= bytesRead)) Stream.emit(Chunk.empty)
          else
            Stream
              .eval(readChunk(obj, rest))
              .evalTap(ch => logger.trace(s"Got Chunk from db size: ${ch.size} ($range)"))
        }
        .repeat
        .takeThrough(_.size == chunkSize)
        .flatMap(Stream.chunk)

    def load(lom: LargeObjectManager, oid: Long): DbRun[Stream[F, *], Byte] =
      openLORead(lom, oid).mapF(objRes =>
        Stream
          .resource(objRes)
          .flatMap(obj => Stream.eval(setLimits(obj)).drain ++ readAll(obj))
      )

    for {
      _ <- DbRun(_ => Stream.eval(logger.debug(s"Load ${id.id} range $range")))
      lom <- loManager.mapF(Stream.eval)
      oid <- findOid(id).mapF(Stream.eval)
      res <- oid match {
        case Some(id) =>
          load(lom, id)
        case None =>
          DbRun(_ => Stream.empty.covary[F])
      }
    } yield res
  }

//  private def convert: F ~> DbRun[F, *] =
//    λ[FunctionK[F, DbRun[F, *]]](fa => DbRun(_ => fa))
}
