package binny.jdbc

final case class JdbcAttrConfig(table: String) {}

object JdbcAttrConfig {
  val default = JdbcAttrConfig("file_attr")
}
