package binny.s3

import binny._

trait S3KeyMapping extends (BinaryId => S3Key)

object S3KeyMapping {

  def constant(bucket: String): S3KeyMapping =
    id => S3Key(bucket, id.id)

  def prefixN(n: Int, defaultBucket: String = "root"): S3KeyMapping =
    id =>
      id.id.splitAt(n) match {
        case (a, "") =>
          S3Key(defaultBucket, a)
        case (a, b) =>
          S3Key(a, b)
      }

  def splitOn(c: Char, defaultBucket: String = "root"): S3KeyMapping =
    id =>
      id.id.span(_ != c) match {
        case (f, "") =>
          S3Key(defaultBucket, f)
        case (bid, oid) =>
          S3Key(bid, oid.drop(1))
      }

}
