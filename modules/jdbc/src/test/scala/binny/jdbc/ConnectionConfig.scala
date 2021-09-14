package binny.jdbc

import javax.sql.DataSource

import binny.util.Logger
import cats.effect._
import org.h2.jdbcx.JdbcDataSource
import org.mariadb.jdbc.MariaDbDataSource
import org.postgresql.ds.PGSimpleDataSource

final case class ConnectionConfig(
    url: String,
    user: String,
    password: String
) {

  val dbms: Dbms =
    Dbms.unsafeFromJdbcUrl(url)

  def dataSource: DataSource =
    dbms match {
      case Dbms.PostgreSQL =>
        val ds = new PGSimpleDataSource()
        ds.setUser(user)
        ds.setPassword(password)
        ds.setURL(url)
        ds

      case Dbms.H2 =>
        val ds = new JdbcDataSource()
        ds.setUser(user)
        ds.setPassword(password)
        ds.setURL(url)
        ds

      case Dbms.MariaDB =>
        val ds = new MariaDbDataSource()
        ds.setUser(user)
        ds.setPassword(password)
        ds.setUrl(url)
        ds
    }

  def setup[F[_]: Sync](dataTable: String, attrTable: String)(implicit
      log: Logger[F]
  ): F[Int] =
    dbms match {
      case Dbms.PostgreSQL =>
        DatabaseSetup.runBoth(Dbms.PostgreSQL, dataSource, dataTable, attrTable)

      case Dbms.H2 =>
        DatabaseSetup.runBoth(Dbms.PostgreSQL, dataSource, dataTable, attrTable)

      case Dbms.MariaDB =>
        DatabaseSetup.runBoth(Dbms.MariaDB, dataSource, dataTable, attrTable)
    }
}

object ConnectionConfig {

  def h2Memory(name: String) =
    ConnectionConfig(
      s"jdbc:h2:mem:$name;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
      "sa",
      ""
    )

  def h2File(path: String): ConnectionConfig =
    ConnectionConfig(s"jdbc:h2:$path;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE", "sa", "")
}
