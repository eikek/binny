package binny.jdbc.impl

import binny.jdbc.Dbms
import binny.util.Logger
import cats.effect.kernel.Sync

/** Provides some table definitions that work with [[binny.jdbc.JdbcBinaryStore]]. Of
  * course, tables can be created by other means, they must have at least the column
  * definitions presented here.
  */
final class CreateDataTable[F[_]: Sync](name: String, log: Logger[F]) {
  implicit private val logger: Logger[F] = log

  /** Create the data table for postgres */
  def postgresData: DbRun[F, Int] =
    DbRun.executeUpdate[F](
      s"""
         |CREATE TABLE IF NOT EXISTS "$name" (
         |  "file_id" varchar(254) not null,
         |  "chunk_nr" int not null,
         |  "chunk_len" int not null,
         |  "chunk_data" bytea not null,
         |  primary key ("file_id", "chunk_nr")
         |)""".stripMargin
    )

  /** Create the data table for mariadb */
  def mariadbData: DbRun[F, Int] =
    DbRun.executeUpdate(
      s"""
         |CREATE TABLE IF NOT EXISTS `$name` (
         |  `file_id` varchar(254) not null,
         |  `chunk_nr` int not null,
         |  `chunk_len` int not null,
         |  `chunk_data` mediumblob not null,
         |  primary key (`file_id`, `chunk_nr`)
         |)""".stripMargin
    )

  def truncate: DbRun[F, Int] =
    DbRun.executeUpdate(s"TRUNCATE $name")

  def createData(dbms: Dbms) =
    dbms match {
      case Dbms.PostgreSQL =>
        postgresData

      case Dbms.H2 =>
        postgresData

      case Dbms.MariaDB =>
        mariadbData
    }
}

object CreateDataTable {
  def apply[F[_]: Sync](name: String)(implicit log: Logger[F]): CreateDataTable[F] =
    new CreateDataTable[F](name, log)
}
