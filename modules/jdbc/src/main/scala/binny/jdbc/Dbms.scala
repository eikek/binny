package binny.jdbc

/** Some databases where `DatabaseSetup` can create the schema. It is only PostgreSQL and
  * MariaDB. The H2 database is using the PostgreSQL schema, which works with the
  * `MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE` connection properties.
  */
sealed trait Dbms extends Product {

  def name: String =
    productPrefix.toLowerCase

}

object Dbms {

  case object PostgreSQL extends Dbms
  case object MariaDB extends Dbms
  case object H2 extends Dbms

  def fromString(str: String): Either[String, Dbms] =
    str.toLowerCase match {
      case "postgresql" => Right(PostgreSQL)
      case "postgres"   => Right(PostgreSQL)
      case "h2"         => Right(H2)
      case "mariadb"    => Right(MariaDB)
      case _            => Left(s"Unknown dbms: $str")
    }

  def unsafeFromString(str: String): Dbms =
    fromString(str).fold(sys.error, identity)

  def fromJdbcUrl(url: String): Either[String, Dbms] =
    fromString(url.dropWhile(_ != ':').drop(1).takeWhile(_ != ':'))

  def unsafeFromJdbcUrl(url: String): Dbms =
    fromJdbcUrl(url).fold(sys.error, identity)
}
