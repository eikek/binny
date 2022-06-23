package binny.jdbc.impl

import binny.util.Logger
import cats.effect.kernel.Sync

/** Provides some table definitions that work with [[binny.jdbc.JdbcBinaryStore]]. Of
  * course, tables can be created by other means, they must have at least the column
  * definitions presented here.
  */
object CreateDataTable {

  /** Create the data table for postgres */
  def postgresData[F[_]: Sync](name: String)(implicit log: Logger[F]): DbRun[F, Int] =
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
  def mariadbData[F[_]: Sync](name: String)(implicit log: Logger[F]): DbRun[F, Int] =
    DbRun.executeUpdate(
      s"""
         |CREATE TABLE `$name` (
         |  `file_id` varchar(254) not null,
         |  `chunk_nr` int not null,
         |  `chunk_len` int not null,
         |  `chunk_data` mediumblob not null,
         |  primary key (`file_id`, `chunk_nr`)
         |)""".stripMargin
    )
}
