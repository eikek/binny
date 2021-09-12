package binny.jdbc.impl

import binny.util.Logger
import cats.effect.kernel.Sync

object CreateDataTable {

  def postgresAll[F[_]: Sync](dataTable: String, attrTable: String)(implicit
      log: Logger[F]
  ): DbRun[F, Int] =
    for {
      n1 <- postgresData(dataTable)
      n2 <- postgresAttr(attrTable)
      n3 <- postgresFK(dataTable, attrTable)
    } yield n1 + n2 + n3

  def mariadbAll[F[_]: Sync](dataTable: String, attrTable: String)(implicit
      log: Logger[F]
  ): DbRun[F, Int] =
    for {
      n1 <- mariadbData(dataTable)
      n2 <- mariadbAttr(attrTable)
      n3 <- mariadbFK(dataTable, attrTable)
    } yield n1 + n2 + n3

  def postgresData[F[_]: Sync](name: String)(implicit log: Logger[F]): DbRun[F, Int] =
    DbRun.executeUpdate[F](s"""
        |CREATE TABLE IF NOT EXISTS "${name}" (
        |  file_id varchar(254) not null,
        |  chunk_nr int not null,
        |  chunk_len int not null,
        |  chunk_data bytea not null,
        |  primary key (file_id, chunk_nr)
        |)""".stripMargin)

  def postgresAttr[F[_]: Sync](name: String)(implicit log: Logger[F]): DbRun[F, Int] =
    DbRun.executeUpdate(s"""
         |CREATE TABLE IF NOT EXISTS "${name}" (
         |  file_id varchar(254) not null,
         |  sha256 varchar(254) not null,
         |  content_type varchar(254) not null,
         |  length bigint not null,
         |  primary key (file_id)
         |)""".stripMargin)

  def postgresFK[F[_]: Sync](
      dataTable: String,
      attrTable: String
  )(implicit log: Logger[F]): DbRun[F, Int] =
    DbRun.executeUpdate(
      s"""ALTER TABLE "$dataTable" ADD CONSTRAINT "${dataTable}_file_id_fkey" FOREIGN KEY ("file_id") REFERENCES "$attrTable"("file_id") """
    )

  def mariadbData[F[_]: Sync](name: String)(implicit log: Logger[F]): DbRun[F, Int] =
    DbRun.executeUpdate(s"""
         |CREATE TABLE ${name} (
         |  file_id varchar(254) not null,
         |  chunk_nr int not null,
         |  chunk_len int not null,
         |  chunk_data mediumblob not null,
         |  primary key (file_id, chunk_nr)
         |)""".stripMargin)

  def mariadbAttr[F[_]: Sync](name: String)(implicit log: Logger[F]): DbRun[F, Int] =
    postgresAttr(name)

  def mariadbFK[F[_]: Sync](dataTable: String, attrTable: String)(implicit
      log: Logger[F]
  ): DbRun[F, Int] =
    DbRun.executeUpdate(
      s"""ALTER TABLE `$dataTable` ADD CONSTRAINT `${dataTable}_file_id_fkey` FOREIGN KEY `file_id` REFERENCES `${attrTable}`(`file_id`)"""
    )

}
