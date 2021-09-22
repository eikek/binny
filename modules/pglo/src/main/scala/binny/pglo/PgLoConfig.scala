package binny.pglo

import binny.ContentTypeDetect

final case class PgLoConfig(table: String, chunkSize: Int, detect: ContentTypeDetect) {
  def withContentTypeDetect(dt: ContentTypeDetect): PgLoConfig =
    copy(detect = dt)
}

object PgLoConfig {

  val default = PgLoConfig("file_lo", 512 * 1024, ContentTypeDetect.probeFileType)
}
