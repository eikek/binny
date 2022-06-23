package binny.jdbc

import javax.sql.DataSource

import binny.jdbc.impl.CreateDataTable
import binny.jdbc.impl.Implicits._
import binny.util.Logger
import cats.effect._

object DatabaseSetup {

  def runData[F[_]: Sync](dbms: Dbms, ds: DataSource, table: String)(implicit
      log: Logger[F]
  ): F[Int] =
    dbms match {
      case Dbms.PostgreSQL =>
        CreateDataTable.postgresData(table).execute(ds)

      case Dbms.H2 =>
        CreateDataTable.postgresData(table).execute(ds)

      case Dbms.MariaDB =>
        CreateDataTable.mariadbData(table).execute(ds)
    }
}
