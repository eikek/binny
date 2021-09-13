package binny.s3

import binny.ContentTypeDetect
import eu.timepit.refined.api.Refined
import fs2.aws.s3.{BucketName, PartSizeMB}

final case class S3Config(
    partSize: PartSizeMB,
    mapping: KeyMapping,
    detect: ContentTypeDetect
)

object S3Config {
  def default(bucket: BucketName) =
    S3Config(Refined.unsafeApply(6), KeyMapping.constant(bucket), ContentTypeDetect.none)
}
