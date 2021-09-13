package binny.jdbc

sealed trait Dbms extends Product {

  def name: String =
    productPrefix.toLowerCase

}

object Dbms {

  case object PostgreSQL extends Dbms
  case object MariaDB extends Dbms
}
