package binny.minio

import binny.BinaryId

final case class S3Key(bucket: String, objectName: String) {

  def changeObjectName(f: String => String): S3Key =
    copy(objectName = f(objectName))

  def withObjectName(name: String): S3Key =
    copy(objectName = name)

  def changeBucket(f: String => String): S3Key =
    copy(bucket = f(bucket))

  def asPath: String = s"$bucket/$objectName"
}

object S3Key {
  def of(bucket: String, id: BinaryId): S3Key =
    S3Key(bucket, id.id)
}
