package binny.jdbc

import binny.jdbc.impl.{CreateDataTable, DbRun}
import binny.jdbc.impl.Implicits._
import binny.util.Logger

import javax.sql.DataSource
import cats.effect._

object DatabaseSetup {

  def run[F[_]: Sync](dbms: Dbms, ds: DataSource, config: JdbcStoreConfig)(implicit
      log: Logger[F]
  ): F[Int] =
    dbms match {
      case Dbms.PostgreSQL =>
        postgres[F](ds, config)

      case Dbms.MariaDB =>
        mariadb[F](ds, config)
    }

  def runAttr[F[_]: Sync](dbms: Dbms, ds: DataSource, table: String)(implicit
      log: Logger[F]
  ): F[Int] =
    dbms match {
      case Dbms.PostgreSQL =>
        CreateDataTable.postgresAttr(table).execute(ds)
      case Dbms.MariaDB =>
        CreateDataTable.mariadbAttr(table).execute(ds)
    }

  def mariadb[F[_]: Sync](ds: DataSource, config: JdbcStoreConfig)(implicit
      log: Logger[F]
  ): F[Int] = {
    val setup: DbRun[F, Int] = config.metaTable
      .map(mt => CreateDataTable.mariadbAll(config.dataTable, mt))
      .getOrElse(CreateDataTable.mariadbData(config.dataTable))
    setup.execute(ds)
  }

  def postgres[F[_]: Sync](ds: DataSource, config: JdbcStoreConfig)(implicit
      log: Logger[F]
  ): F[Int] = {
    val setup: DbRun[F, Int] = config.metaTable
      .map(mt => CreateDataTable.postgresAll(config.dataTable, mt))
      .getOrElse(CreateDataTable.postgresData(config.dataTable))
    setup.execute(ds)
  }
}
