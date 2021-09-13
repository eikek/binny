package binny.s3

import eu.timepit.refined.types.string.NonEmptyString
import fs2.aws.s3.{BucketName, FileKey}

final case class S3Key(bucket: BucketName, key: FileKey)

object S3Key {

  def unsafe(bucket: String, key: String): S3Key =
    S3Key(
      BucketName(NonEmptyString.unsafeFrom(bucket)),
      FileKey(NonEmptyString.unsafeFrom(key))
    )
}
