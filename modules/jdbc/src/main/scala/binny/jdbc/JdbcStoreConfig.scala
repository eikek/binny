package binny.jdbc

import binny.ContentTypeDetect

final case class JdbcStoreConfig(
    dataTable: String,
    metaTable: Option[String],
    chunkSize: Int,
    detect: ContentTypeDetect
)

object JdbcStoreConfig {

  val default =
    JdbcStoreConfig("file_chunk", Some("file_attr"), 512 * 1024, ContentTypeDetect.none)

}
