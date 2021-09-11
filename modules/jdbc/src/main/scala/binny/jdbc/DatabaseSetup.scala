package binny.jdbc

import binny.jdbc.impl.{CreateDataTable, DbRun}

import javax.sql.DataSource
import cats.effect.kernel.Sync

object DatabaseSetup {
  import DbRun._

  def run[F[_]: Sync](dbms: Dbms, ds: DataSource, config: JdbcStoreConfig): F[Int] =
    dbms match {
      case Dbms.PostgreSQL =>
        postgres[F](ds, config)

      case Dbms.MariaDB =>
        mariadb[F](ds, config)
    }

  def mariadb[F[_]: Sync](ds: DataSource, config: JdbcStoreConfig): F[Int] = {
    val setup: DbRun[Int] = config.metaTable
      .map(mt => CreateDataTable.mariadbAll(config.dataTable, mt))
      .getOrElse(CreateDataTable.mariadbData(config.dataTable))
    setup.inTX.execute[F](ds)
  }

  def postgres[F[_]: Sync](ds: DataSource, config: JdbcStoreConfig): F[Int] = {
    val setup: DbRun[Int] = config.metaTable
      .map(mt => CreateDataTable.postgresAll(config.dataTable, mt))
      .getOrElse(CreateDataTable.postgresData(config.dataTable))
    setup.inTX.execute[F](ds)
  }
}
