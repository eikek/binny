package binny.jdbc.impl

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.sql.ResultSet
import scala.util.Using
import binny.ContentTypeDetect.Hint
import binny._
import binny.jdbc.impl.Implicits._
import binny.util.{Logger, RangeCalc}
import cats.data.OptionT
import cats.effect._
import cats.implicits._
import fs2.Chunk
import fs2.Stream
import scodec.bits.ByteVector

final class DbRunApi[F[_]: Sync](table: String, logger: Logger[F]) {
  implicit private val log: Logger[F] = logger

  def exists(id: BinaryId): DbRun[OptionT[F, *], Unit] =
    DbRun
      .query(s"SELECT file_id FROM $table WHERE file_id = ? LIMIT ?") { ps =>
        ps.setString(1, id.id)
        ps.setInt(2, 1)
      }
      .use(DbRun.readOpt[F, Unit](_ => ()))
      .mapF(OptionT.apply)

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
        ps.setBinaryStream(4, new ByteArrayInputStream(bytes.toArraySlice.values))
      }

  def insertAllData(
      id: BinaryId,
      data: Stream[F, Chunk[Byte]]
  ): Stream[F, DbRun[F, Int]] = {
    val bytes =
      data.take(1).ifEmpty(Stream.emit(Chunk.empty[Byte])) ++
        data.drop(1)

    bytes.zipWithIndex.map { case (chunk, index) =>
      insertChunk(id, index.toInt, chunk)
    }
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
      }
      .use(DbRun.readOpt[F, Chunk[Byte]](rs => Chunk.array(rs.getBytes(1))))

  def queryAll(
      id: BinaryId,
      range: ByteRange,
      chunkSize: Int
  ): DbRun[Stream[F, *], Byte] = {
    val offsets = RangeCalc.calcOffset(range, chunkSize)
    val all = range match {
      case ByteRange.All =>
        DbRun.query(
          s"SELECT chunk_data FROM $table WHERE file_id = ? ORDER BY chunk_nr ASC"
        )(_.setString(1, id.id))
      case ByteRange.Chunk(_, _) =>
        DbRun.query(
          s"SELECT chunk_data,chunk_nr FROM $table WHERE file_id = ? AND chunk_nr >= ? AND chunk_nr <= ? ORDER BY chunk_nr ASC"
        ) { stmt =>
          stmt.setString(1, id.id)
          stmt.setInt(2, offsets.firstChunk)
          stmt.setInt(3, offsets.lastChunk)
        }
    }

    def readRow(rs: ResultSet, buf: Array[Byte]): F[Chunk[Byte]] =
      Sync[F].blocking(if (rs.next) rs.getBinaryStream(1).read(buf) else -1).map {
        case n if n <= 0 => Chunk.empty
        case n if n < buf.length =>
          val ch = Chunk.array(buf, 0, n)
          if (offsets.isNone) ch
          else RangeCalc.chop(ch, offsets, rs.getInt(2))
        case _ =>
          val ch = Chunk.array(buf)
          if (offsets.isNone) ch
          else RangeCalc.chop(ch, offsets, rs.getInt(2))
      }

    def useResultSet(rs: ResultSet, buf: F[Array[Byte]]): Stream[F, Byte] =
      Stream
        .eval(buf.flatMap(b => readRow(rs, b)))
        .repeat
        .takeThrough(_.nonEmpty)
        .flatMap(Stream.chunk)

    val newBuf = Sync[F].delay(new Array[Byte](chunkSize))

    all.mapF(rsRes => Stream.resource(rsRes).flatMap(useResultSet(_, newBuf)))
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
        Using.resource(ps.executeQuery()) { rs =>
          var len = 0L
          val md = MessageDigest.getInstance("SHA-256")
          var ct = None: Option[SimpleContentType]
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
    }

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
}
