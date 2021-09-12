package binny.s3

import binny.ContentTypeDetect

final case class S3Config(
    endpoint: String,
    accessKey: String,
    secretKey: String,
    keyMapping: S3KeyMapping,
    chunkSize: Int,
    partSize: Int,
    detect: ContentTypeDetect
) {

  override def toString: String =
    s"S3Config(endpoint=$endpoint, accessKey=$accessKey, secretKey=***)"
}

object S3Config {
  def default(
      endpoint: String,
      accessKey: String,
      secretKey: String,
      km: S3KeyMapping
  ): S3Config =
    S3Config(
      endpoint,
      accessKey,
      secretKey,
      km,
      256 * 1024,
      8 * 1024 * 1024,
      ContentTypeDetect.none
    )
}
