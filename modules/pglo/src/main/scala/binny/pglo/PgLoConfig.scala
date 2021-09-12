package binny.pglo

import binny.ContentTypeDetect

final case class PgLoConfig(table: String, chunkSize: Int, detect: ContentTypeDetect) {}

object PgLoConfig {

  val default = PgLoConfig("file_lo", 512 * 1024, ContentTypeDetect.none)
}
