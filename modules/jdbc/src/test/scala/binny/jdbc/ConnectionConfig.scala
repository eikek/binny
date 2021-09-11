package binny.jdbc

import cats.effect._
import org.h2.jdbcx.JdbcDataSource
import org.mariadb.jdbc.MariaDbDataSource
import org.postgresql.ds.PGSimpleDataSource

import javax.sql.DataSource

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

  def setup[F[_]: Sync](cfg: JdbcStoreConfig): F[Int] =
    dbms match {
      case "h2"         => DatabaseSetup.postgres(dataSource, cfg)
      case "mariadb"    => DatabaseSetup.mariadb(dataSource, cfg)
      case "postgresql" => DatabaseSetup.postgres(dataSource, cfg)
      case _            => sys.error(s"Unknown jdbc url: $url")
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
