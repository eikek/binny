package binny.minio

import binny.{BinaryId, ContentTypeDetect}

final case class MinioConfig(
    endpoint: String,
    accessKey: String,
    secretKey: String,
    keyMapping: S3KeyMapping,
    chunkSize: Int,
    partSize: Int,
    detect: ContentTypeDetect
) {
  def withContentTypeDetect(dt: ContentTypeDetect): MinioConfig =
    copy(detect = dt)

  def makeS3Key(id: BinaryId): S3Key =
    keyMapping.makeS3Key(id)

  def withKeyMapping(km: S3KeyMapping): MinioConfig =
    copy(keyMapping = km)

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
      ContentTypeDetect.probeFileType
    )
}
