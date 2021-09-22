package binny.jdbc

import binny.ContentTypeDetect

final case class JdbcStoreConfig(
    dataTable: String,
    chunkSize: Int,
    detect: ContentTypeDetect
) {

  def withContentTypeDetect(dt: ContentTypeDetect): JdbcStoreConfig =
    copy(detect = dt)

  def withChunkSize(cs: Int): JdbcStoreConfig =
    copy(chunkSize = cs)
}

object JdbcStoreConfig {

  val default =
    JdbcStoreConfig("file_chunk", 256 * 1024, ContentTypeDetect.probeFileType)

}
