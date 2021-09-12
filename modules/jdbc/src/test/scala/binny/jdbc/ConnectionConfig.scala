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

  val dbms: String =
    url.dropWhile(_ != ':').drop(1).takeWhile(_ != ':')

  def dataSource: DataSource =
    dbms match {
      case "h2" =>
        val ds = new JdbcDataSource()
        ds.setUser(user)
        ds.setPassword(password)
        ds.setURL(url)
        ds

      case "mariadb" =>
        val ds = new MariaDbDataSource()
        ds.setUser(user)
        ds.setPassword(password)
        ds.setUrl(url)
        ds

      case "postgresql" =>
        val ds = new PGSimpleDataSource()
        ds.setUser(user)
        ds.setPassword(password)
        ds.setURL(url)
        ds

      case _ =>
        sys.error(s"Unknown jdbc url: $url")
    }

  def setup[F[_]: Sync](dataTable: String, attrTable: String)(implicit
      log: Logger[F]
  ): F[Int] =
    dbms match {
      case "h2" =>
        DatabaseSetup.runBoth(Dbms.PostgreSQL, dataSource, dataTable, attrTable)
      case "mariadb" =>
        DatabaseSetup.runBoth(Dbms.MariaDB, dataSource, dataTable, attrTable)
      case "postgresql" =>
        DatabaseSetup.runBoth(Dbms.PostgreSQL, dataSource, dataTable, attrTable)
      case _ => sys.error(s"Unknown jdbc url: $url")
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
