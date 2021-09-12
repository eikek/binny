package binny.jdbc

import binny.ContentTypeDetect

final case class JdbcStoreConfig(
    dataTable: String,
    chunkSize: Int,
    detect: ContentTypeDetect
)

object JdbcStoreConfig {

  val default =
    JdbcStoreConfig("file_chunk", 512 * 1024, ContentTypeDetect.none)

}
