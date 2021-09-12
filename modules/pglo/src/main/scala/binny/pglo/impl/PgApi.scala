package binny.pglo.impl

import java.security.MessageDigest

import binny.ContentTypeDetect.Hint
import binny._
import binny.jdbc.impl.DbRun
import binny.jdbc.impl.Implicits._
import binny.util.Logger
import cats.arrow.FunctionK
import cats.effect._
import cats.implicits._
import cats.~>
import fs2.{Chunk, Stream}
import org.postgresql.PGConnection
import org.postgresql.largeobject.{LargeObject, LargeObjectManager}
import scodec.bits.ByteVector

final class PgApi[F[_]: Sync](table: String, logger: Logger[F]) {
  implicit private val log = logger

  def createTable: DbRun[F, Int] =
    DbRun.executeUpdate(s"""
         |CREATE TABLE IF NOT EXISTS "$table" (
         |  "file_id" varchar(254) NOT NULL PRIMARY KEY,
         |  "data_oid" oid
         |);
         |""".stripMargin)

  def loManager: DbRun[F, LargeObjectManager] =
    DbRun.delay(_.unwrap(classOf[PGConnection]).getLargeObjectAPI)

  def loManagerS: Stream[DbRun[F, *], LargeObjectManager] =
    Stream.eval(loManager)

  def createLO(lom: LargeObjectManager): DbRun[F, Long] =
    DbRun.delay(_ => lom.createLO(LargeObjectManager.READWRITE))

  def openLOWrite(): DbRun[Resource[F, *], LargeObject] =
    for {
      _   <- DbRun.makeTX
      lom <- loManager.mapF(Resource.eval)
      oid <- createLO(lom).mapF(Resource.eval)
      _   <- DbRun(_ => Resource.eval(logger.trace(s"Open lo ${oid} for writing")))
      lo  <- DbRun.resource(_ => lom.open(oid))(_.close())
    } yield lo

  def openLORead(lom: LargeObjectManager, oid: Long): DbRun[Resource[F, *], LargeObject] =
    for {
      _  <- DbRun.makeTX
      _  <- DbRun(_ => Resource.eval(logger.trace(s"Open lo ${oid} for reading")))
      lo <- DbRun.resource(_ => lom.open(oid, LargeObjectManager.READWRITE))(_.close())
    } yield lo

  def findOid(id: BinaryId): DbRun[F, Option[Long]] =
    DbRun
      .query[F](s"""SELECT data_oid FROM "$table" WHERE "file_id" = ?""")(
        _.setString(1, id.id)
      )
      .use(DbRun.readOpt[F, Long](_.getLong(1)))

  def insert(id: BinaryId, bytes: Stream[F, Chunk[Byte]]): DbRun[F, Int] = {
    val copy: DbRun[F, (Long, Int)] =
      for {
        res <- openLOWrite().use(obj =>
          bytes
            .evalMap(chunk =>
              Sync[F]
                .blocking {
                  obj.write(chunk.toArraySlice.values)
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
      _   <- DbRun.delay(_ => oid.foreach(id => lom.delete(id)))
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
          var len    = 0L
          val md     = MessageDigest.getInstance("SHA-256")
          var ct     = (None: Option[SimpleContentType])

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

  def load(id: BinaryId, range: ByteRange, chunkSize: Int): Stream[DbRun[F, *], Byte] = {
    val data: Stream[DbRun[F, *], Stream[F, Byte]] =
      for {
        lom <- Stream.eval(loManager)
        oid <- Stream.eval(findOid(id))
        data <- Stream.eval(oid match {
          case Some(id) =>
            openLORead(lom, id).mapF(objRes =>
              Sync[F].delay {
                Stream
                  .resource(objRes)
                  .flatMap { obj =>
                    range match {
                      case ByteRange.All =>
                        ()
                      case ByteRange.Chunk(offset, length) =>
                        obj.seek(offset.toInt)
                        obj.truncate(length.toInt + offset.toInt)
                    }
                    loToStream(obj, chunkSize)
                  }
              }
            )

          case None =>
            DbRun.pure[F, Stream[F, Byte]](Stream.empty.covary[F])
        })
      } yield data

    data.flatMap(_.translate(convert))
  }

  private def loToStream(obj: LargeObject, chunkSize: Int): Stream[F, Byte] =
    Stream
      .eval(
        logger.trace(s"Fetching chunk for lo ${obj.getLongOID}") *> Sync[F]
          .blocking(Chunk.array(obj.read(chunkSize)))
          .flatTap(oc => logger.trace(s"Fetched chunk of size ${oc.size}"))
      )
      .repeat
      .takeThrough(_.size > 0)
      .flatMap(Stream.chunk)

  private def convert: F ~> DbRun[F, *] =
    λ[FunctionK[F, DbRun[F, *]]](fa => DbRun(_ => fa))
}
