package binny.s3

import binny._
import eu.timepit.refined.types.string.NonEmptyString
import fs2.aws.s3.{BucketName, FileKey}

trait KeyMapping extends (BinaryId => S3Key) {}

object KeyMapping {

  def constant(bucket: BucketName): KeyMapping =
    id => S3Key(bucket, FileKey(NonEmptyString.unsafeFrom(id.id)))
}
