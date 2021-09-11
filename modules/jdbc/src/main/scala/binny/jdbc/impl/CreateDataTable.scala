package binny.jdbc.impl

import cats.implicits._

object CreateDataTable {

  def postgresAll(dataTable: String, attrTable: String): DbRun[Int] =
    for {
      n1 <- postgresData(dataTable)
      n2 <- postgresAttr(attrTable)
      n3 <- postgresFK(dataTable, attrTable)
    } yield n1 + n2 + n3

  def mariadbAll(dataTable: String, attrTable: String): DbRun[Int] =
    for {
      n1 <- mariadbData(dataTable)
      n2 <- mariadbAttr(attrTable)
      n3 <- mariadbFK(dataTable, attrTable)
    } yield n1 + n2 + n3

  def postgresData(name: String): DbRun[Int] =
    DbRun.execUpdate(s"""
        |CREATE TABLE "${name}" (
        |  file_id varchar(254) not null,
        |  chunk_nr int not null,
        |  chunk_len int not null,
        |  chunk_data bytea not null,
        |  primary key (file_id, chunk_nr)
        |)""".stripMargin)

  def postgresAttr(name: String): DbRun[Int] =
    DbRun.execUpdate(s"""
         |CREATE TABLE "${name}" (
         |  file_id varchar(254) not null,
         |  sha256 varchar(254) not null,
         |  content_type varchar(254) not null,
         |  length bigint not null,
         |  primary key (file_id)
         |)""".stripMargin)

  private def postgresFK(dataTable: String, attrTable: String): DbRun[Int] =
    DbRun.execUpdate(
      s"""ALTER TABLE "$dataTable" ADD CONSTRAINT "${dataTable}_file_id_fkey" FOREIGN KEY ("file_id") REFERENCES "$attrTable"("file_id") """
    )

  def mariadbData(name: String): DbRun[Int] =
    DbRun.execUpdate(s"""
         |CREATE TABLE ${name} (
         |  file_id varchar(254) not null,
         |  chunk_nr int not null,
         |  chunk_len int not null,
         |  chunk_data mediumblob not null,
         |  primary key (file_id, chunk_nr)
         |)""".stripMargin)

  def mariadbAttr(name: String): DbRun[Int] =
    postgresAttr(name)

  private def mariadbFK(dataTable: String, attrTable: String): DbRun[Int] =
    DbRun.execUpdate(
      s"""ALTER TABLE `$dataTable` ADD CONSTRAINT `${dataTable}_file_id_fkey` FOREIGN KEY `file_id` REFERENCES `${attrTable}`(`file_id`)"""
    )

}
