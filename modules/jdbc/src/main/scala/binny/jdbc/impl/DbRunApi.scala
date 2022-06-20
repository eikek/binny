package binny.jdbc.impl

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.sql.ResultSet
import javax.sql.DataSource

import scala.collection.mutable.ListBuffer
import scala.util.Using

import binny.jdbc.impl.DbRunApi.ChunkInfo
import binny.jdbc.impl.Implicits._
import binny.util.RangeCalc.Offsets
import binny.util.{Logger, RangeCalc}
import binny.{InsertChunkResult, _}
import cats.data.OptionT
import cats.effect._
import cats.implicits._
import fs2.Chunk
import fs2.Stream
import scodec.bits.ByteVector

final class DbRunApi[F[_]: Sync](table: String, logger: Logger[F]) {
  implicit private val log: Logger[F] = logger

  def insertNextChunk(
      id: BinaryId,
      chunkIndex: Int,
      chunksTotal: Int,
      bytes: Chunk[Byte]
  ): DbRun[F, InsertChunkResult] = {
    val insert =
      removeChunk(id, chunkIndex).inTX *>
        insertChunk(id, chunkIndex, bytes).inTX

    insert *> count(id).map { currentChunks =>
      if (currentChunks == chunksTotal) InsertChunkResult.complete
      else InsertChunkResult.incomplete
    }
  }

  def removeChunk(id: BinaryId, chunkIndex: Int): DbRun[F, Int] =
    DbRun.update(s"DELETE FROM $table WHERE file_id = ? AND chunk_nr = ?") { ps =>
      ps.setString(1, id.id)
      ps.setInt(2, chunkIndex)
    }

  def countAll(): DbRun[F, Long] =
    DbRun
      .query(s"SELECT COUNT(*) FROM $table")(_ => ())
      .use(DbRun.readOpt[F, Long](_.getLong(1)))
      .map(_.getOrElse(0L))

  def count(id: BinaryId): DbRun[F, Long] =
    DbRun
      .query(s"SELECT COUNT(*) FROM $table WHERE file_id = ?")(_.setString(1, id.id))
      .use(DbRun.readOpt[F, Long](_.getLong(1)))
      .map(_.getOrElse(0L))

  def exists(id: BinaryId): DbRun[OptionT[F, *], Unit] =
    DbRun
      .query(s"SELECT file_id FROM $table WHERE file_id = ? LIMIT ?") { ps =>
        ps.setString(1, id.id)
        ps.setInt(2, 1)
      }
      .use(DbRun.readOpt[F, Unit](_ => ()))
      .mapF(OptionT.apply)

  def listIdsChunk(
      start: String,
      prefix: Option[String],
      chunkSize: Int
  ): DbRun[F, Chunk[BinaryId]] = {
    val clause = prefix.map(_ => s"AND file_id like ?").getOrElse("")
    val select =
      DbRun.query(
        s"SELECT DISTINCT file_id FROM $table WHERE file_id > ? $clause ORDER BY file_id"
      ) { ps =>
        ps.setString(1, start)
        prefix.map(p => ps.setString(2, s"$p%")).getOrElse(())
      }

    def readRows(resultSet: ResultSet): F[Chunk[BinaryId]] =
      Sync[F].blocking {
        val buffer = ListBuffer.empty[BinaryId]
        while (resultSet.next() && buffer.size < chunkSize)
          buffer += BinaryId(resultSet.getString(1))
        Chunk.seq(buffer)
      }

    DbRun
      .makeTX[F]
      .flatMap(_ => select)
      .mapF(_.use(readRows))
  }

  def listAllIds(
      prefix: Option[String],
      chunkSize: Int,
      ds: DataSource
  ): Stream[F, BinaryId] = {
    def selectNext(start: String): Stream[F, BinaryId] =
      Stream
        .eval(listIdsChunk(start, prefix, chunkSize).execute(ds))
        .flatMap { chunk =>
          chunk.last match {
            case Some(lastId) if chunk.size == chunkSize =>
              Stream.chunk(chunk) ++ selectNext(lastId.id)
            case _ =>
              Stream.chunk(chunk)
          }
        }

    selectNext("")
  }

  def insertEmptyAttr(id: BinaryId): DbRun[F, Int] =
    DbRun.update(
      s"INSERT INTO $table (file_id, sha256, content_type, length) VALUES (?,?,?,?)"
    ) { ps =>
      ps.setString(1, id.id)
      ps.setString(2, "")
      ps.setString(3, "")
      ps.setLong(4, 0L)
    }

  def insertAttr(id: BinaryId, attr: BinaryAttributes): DbRun[F, Int] =
    DbRun.update(
      s"INSERT INTO $table (file_id, sha256, content_type, length) VALUES (?,?,?,?)"
    ) { ps =>
      ps.setString(1, id.id)
      ps.setString(2, attr.sha256.toHex)
      ps.setString(3, attr.contentType.contentType)
      ps.setLong(4, attr.length)
    }

  def updateAttr(id: BinaryId, attr: BinaryAttributes): DbRun[F, Int] =
    DbRun.update(
      s"UPDATE $table SET sha256=?,content_type=?,length=? WHERE file_id = ?"
    ) { ps =>
      ps.setString(1, attr.sha256.toHex)
      ps.setString(2, attr.contentType.contentType)
      ps.setLong(3, attr.length)
      ps.setString(4, id.id)
    }

  def insertChunk(id: BinaryId, index: Int, bytes: Chunk[Byte]): DbRun[F, Int] =
    DbRun(_ => logger.trace(s"Insert chunk $index of size ${bytes.size}")) >>
      DbRun.update(
        s"INSERT INTO $table (file_id, chunk_nr, chunk_len, chunk_data) VALUES (?,?,?,?)"
      ) { ps =>
        ps.setString(1, id.id)
        ps.setInt(2, index)
        ps.setInt(3, bytes.size)
        val bs = bytes.toArraySlice
        ps.setBinaryStream(4, new ByteArrayInputStream(bs.values, bs.offset, bs.size))
      }

  def insertAllData(
      id: BinaryId,
      data: Stream[F, Chunk[Byte]]
  ): Stream[F, DbRun[F, Int]] =
    data.ifEmpty(Stream.emit(Chunk.empty[Byte])).zipWithIndex.map { case (chunk, index) =>
      insertChunk(id, index.toInt, chunk)
    }

  def delete(id: BinaryId): DbRun[F, Int] =
    DbRun.update(s"DELETE FROM $table WHERE file_id = ?") { ps =>
      ps.setString(1, id.id)
    }

  def queryChunk(id: BinaryId, chunkNr: Int): DbRun[F, Option[Chunk[Byte]]] =
    DbRun
      .query(s"SELECT chunk_data FROM $table WHERE file_id = ? AND chunk_nr = ?") { ps =>
        ps.setString(1, id.id)
        ps.setInt(2, chunkNr)
        ps.setMaxRows(1)
        ps.setFetchSize(1)
      }
      .use(DbRun.readOpt[F, Chunk[Byte]](rs => Chunk.array(rs.getBytes(1))))
      .inTX

  def queryAll(
      id: BinaryId,
      range: ByteRange
  ): DbRun[Stream[F, *], Byte] =
    getChunkSize(id).mapF(Stream.eval).flatMap {
      case ChunkInfo.None =>
        DbRun(_ => Binary.empty[F])

      case ChunkInfo.Single =>
        queryChunk(id, 0)
          .map {
            case Some(c) =>
              range match {
                case ByteRange.All =>
                  c

                case ByteRange.Chunk(offset, length) =>
                  val offsets = Offsets(0, 1, offset.toInt, length)
                  RangeCalc.chop(c, offsets, 0)
              }

            case None =>
              Chunk.empty[Byte]
          }
          .mapF(Stream.eval)
          .mapF(_.flatMap(Stream.chunk))

      case ChunkInfo.Multiple(chunkSize) =>
        queryAllMultiple(id, range, chunkSize)
    }

  private def queryAllMultiple(
      id: BinaryId,
      range: ByteRange,
      chunkSize: Int
  ): DbRun[Stream[F, *], Byte] = {
    def readRow(rs: ResultSet, offsets: Offsets): F[Chunk[Byte]] =
      Sync[F].blocking(if (rs.next) Option(rs.getBytes(1)) else None).map {
        case None => Chunk.empty
        case Some(buf) =>
          val ch = Chunk.array(buf)
          if (offsets.isNone) ch
          else RangeCalc.chop(ch, offsets, rs.getInt(2))
      }

    def useResultSet(rs: ResultSet, offsets: Offsets): Stream[F, Byte] =
      Stream
        .eval(readRow(rs, offsets))
        .repeat
        .takeThrough(_.nonEmpty)
        .flatMap(Stream.chunk)

    val offsets = RangeCalc.calcOffset(range, chunkSize)
    val all = range match {
      case ByteRange.All =>
        DbRun.query(
          s"SELECT chunk_data FROM $table WHERE file_id = ? ORDER BY chunk_nr ASC"
        ) { ps =>
          ps.setString(1, id.id)
          ps.setFetchSize(1)
        }
      case ByteRange.Chunk(_, _) =>
        DbRun.query(
          s"SELECT chunk_data,chunk_nr FROM $table WHERE file_id = ? AND chunk_nr >= ? AND chunk_nr <= ? ORDER BY chunk_nr ASC"
        ) { ps =>
          ps.setString(1, id.id)
          ps.setInt(2, offsets.firstChunk)
          ps.setInt(3, offsets.lastChunk)
          ps.setFetchSize(1)
        }
    }

    DbRun
      .makeTX[F]
      .mapF(Stream.resource[F, Unit])
      .flatMap(_ =>
        all.mapF(rsRes => Stream.resource(rsRes).flatMap(useResultSet(_, offsets)))
      )
  }

  def computeAttr(
      id: BinaryId,
      detect: ContentTypeDetect,
      hint: Hint
  ): DbRun[F, BinaryAttributes] =
    DbRun.delay { conn =>
      val sql = s"SELECT chunk_data FROM $table WHERE file_id = ? ORDER BY chunk_nr ASC"
      Using.resource(conn.prepareStatement(sql)) { ps =>
        ps.setString(1, id.id)
        ps.setFetchSize(1)
        Using.resource(ps.executeQuery()) { rs =>
          var len = 0L
          val md = MessageDigest.getInstance("SHA-256")
          var ct = None: Option[SimpleContentType]
          rs.setFetchSize(1)
          while (rs.next()) {
            val data = rs.getBytes(1)
            md.update(data)
            len = len + data.length
            if (ct.isEmpty) {
              ct = Some(detect.detect(ByteVector.view(data), hint))
            }
          }
          BinaryAttributes(
            ByteVector.view(md.digest()),
            ct.getOrElse(SimpleContentType.octetStream),
            len
          )
        }
      }
    }.inTX // necessary so that setFetchSize is effective

  def queryAttr(id: BinaryId): DbRun[F, Option[BinaryAttributes]] =
    DbRun
      .query(
        s"SELECT sha256, content_type, length FROM $table WHERE file_id = ? AND sha256 <> ?"
      ) { ps =>
        ps.setString(1, id.id)
        ps.setString(2, "")
      }
      .use(
        DbRun.readOpt[F, BinaryAttributes](rs =>
          BinaryAttributes(
            ByteVector.fromValidHex(rs.getString(1)),
            SimpleContentType(rs.getString(2)),
            rs.getLong(3)
          )
        )
      )

  def getChunkSize(id: BinaryId): DbRun[F, ChunkInfo] = {
    val q =
      DbRun.query(s"SELECT chunk_len FROM $table WHERE file_id = ? LIMIT ?") { ps =>
        ps.setString(1, id.id)
        ps.setInt(2, 2)
      }
    q.use(rs =>
      Sync[F].blocking {
        if (rs.next()) {
          val size = rs.getInt(1)
          if (rs.next()) ChunkInfo.Multiple(size)
          else ChunkInfo.Single
        } else {
          ChunkInfo.None
        }
      }
    )
  }
}

object DbRunApi {
  sealed trait ChunkInfo
  object ChunkInfo {
    case object None extends ChunkInfo
    case object Single extends ChunkInfo
    case class Multiple(size: Int) extends ChunkInfo
  }
}
