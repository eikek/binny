package binny.jdbc.impl

import binny.{BinaryAttributes, BinaryData, BinaryId}
import cats.effect.Resource

import java.sql.{PreparedStatement, ResultSet}
import fs2.{Chunk, Stream}

import java.io.ByteArrayInputStream

trait DbRunApi extends DbRunInstances {

  def pure[A](a: A): DbRun[A] =
    DbRun(_ => a)

  def defer[A](a: => A): DbRun[A] =
    DbRun(_ => a)

  def execUpdate(stmt: String): DbRun[Int] =
    DbRun(_.createStatement().executeUpdate(stmt))

  def prepare(stmt: String): Resource[DbRun, PreparedStatement] =
    Resource.make(DbRun(_.prepareStatement(stmt)))(ps => DbRun.defer(ps.close()))

  def commit: DbRun[Unit] =
    DbRun(_.commit())

  def rollback: DbRun[Unit] =
    DbRun(_.rollback())

  def query(ps: PreparedStatement): Resource[DbRun, ResultSet] =
    Resource.make(defer(ps.executeQuery()))(rs => defer(rs.close()))

  def setAutoCommit(flag: Boolean): DbRun[Unit] =
    DbRun(_.setAutoCommit(flag))

  def insertEmptyAttr(table: String, id: BinaryId): DbRun[Int] =
    (for {
      ps <- DbRun.prepare(
        s"""INSERT INTO $table (file_id,sha256,content_type,length) VALUES (?,?,?,?) """
      )
      _ <- Resource.eval(DbRun { _ =>
        ps.setString(1, id.id)
        ps.setString(2, "")
        ps.setString(3, "")
        ps.setLong(4, 0L)
      })
    } yield ps).use(ps => defer(ps.executeUpdate()))

  def insertChunk(
      table: String,
      id: BinaryId,
      index: Int,
      bytes: Chunk[Byte]
  ): DbRun[Int] =
    (for {
      ps <- DbRun.prepare(
        s"""INSERT INTO $table (file_id, chunk_nr, chunk_len, chunk_data) VALUES (?,?,?,?)  """
      )
      _ <- Resource.eval(DbRun.defer {
        ps.setString(1, id.id)
        ps.setInt(2, index)
        ps.setInt(3, bytes.size)
        ps.setBinaryStream(4, new ByteArrayInputStream(bytes.toArraySlice.values))
      })
    } yield ps).use(ps => DbRun.defer(ps.executeUpdate()))

  def insertAllData[F[_]](table: String, data: BinaryData[F]): Stream[F, DbRun[Int]] =
    data.bytes.chunks.zipWithIndex.map { case (chunk, index) =>
      insertChunk(table, data.id, index.toInt, chunk)
    }

  def queryChunk(
      table: String,
      id: BinaryId,
      chunkNr: Int
  ): DbRun[Option[Chunk[Byte]]] =
    (for {
      ps <- DbRun.prepare(
        s"SELECT chunk_data FROM $table WHERE file_id = ? AND chunk_nr = ?"
      )
      _ <- Resource.eval(DbRun.defer {
        ps.setString(1, id.id)
        ps.setInt(2, chunkNr)
        ps.setMaxRows(1)
      })
      rs <- DbRun.query(ps)
    } yield rs).use { rs =>
      if (rs.next) DbRun.defer(Some(Chunk.array(rs.getBytes(1))))
      else DbRun.pure(None)
    }

  def exists(table: String, id: BinaryId) =
    (for {
      ps <- DbRun.prepare(s"SELECT file_id FROM $table WHERE file_id = ? LIMIT 1")
      _ <- Resource.eval(DbRun.defer {
        ps.setString(1, id.id)
      })
      res <- DbRun.query(ps)
    } yield res).use(rs => DbRun.defer(rs.next()))

  def deleteFrom(table: String, id: BinaryId): DbRun[Int] =
    DbRun
      .prepare(s"DELETE FROM $table WHERE file_id = ?")
      .map { ps =>
        ps.setString(1, id.id)
        ps
      }
      .use(ps => DbRun.defer(ps.executeUpdate()))

  def updateAttr(table: String, id: BinaryId, attr: BinaryAttributes): DbRun[Int] =
    (for {
      ps <- DbRun.prepare(
        s"UPDATE $table SET sha256=?,content_type=?,length=? WHERE file_id = ?"
      )
      _ <- Resource.eval(defer {
        ps.setString(1, attr.sha256.toHex)
        ps.setString(2, attr.contentType.contentType)
        ps.setLong(3, attr.length)
        ps.setString(4, id.id)
      })
    } yield ps).use(p => defer(p.executeUpdate()))
}
