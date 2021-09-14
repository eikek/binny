package binny.minio

import binny.ContentTypeDetect

final case class MinioConfig(
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

object MinioConfig {
  def default(
      endpoint: String,
      accessKey: String,
      secretKey: String,
      km: S3KeyMapping
  ): MinioConfig =
    MinioConfig(
      endpoint,
      accessKey,
      secretKey,
      km,
      256 * 1024,
      8 * 1024 * 1024,
      ContentTypeDetect.none
    )
}