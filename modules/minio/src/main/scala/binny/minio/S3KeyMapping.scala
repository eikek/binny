package binny.minio

import binny._

trait S3KeyMapping {

  /** A bucket name must be deduced from a binary-id. */
  def makeS3Key(id: BinaryId): S3Key

  /** When listing objects, it must be known in which buckets to look. */
  def bucketFilter(bucket: String): Boolean
}

object S3KeyMapping {

  def apply(
      f: BinaryId => S3Key,
      filter: String => Boolean
  ): S3KeyMapping =
    new S3KeyMapping {
      def makeS3Key(id: BinaryId) = f(id)
      def bucketFilter(bucket: String) = filter(bucket)
    }

  /** Puts everything into a single bucket. */
  def constant(bucket: String): S3KeyMapping =
    S3KeyMapping(id => S3Key.of(bucket, id), _ == bucket)

  /** Uses the id to generate buckets dynamically: Uses the first part of an id up to the
    * `delimiter` char and concatenates this to the `bucketPrefix`. If the id doesn't
    * contain the delimiter char, it will use `bucketPrefix` as the bucket name.
    *
    * Example:
    * {{{
    *  given: bucketPrefix="myapp", delimiter='/', id=user1/uiae123
    *  creates bucket: myapp-user1
    * }}}
    */
  def prefix(bucketPrefix: String, delimiter: Char): S3KeyMapping = {
    require(bucketPrefix.trim.nonEmpty, "bucketPrefix must be non-empty")
    S3KeyMapping(
      id =>
        id.id.span(_ != delimiter) match {
          case (_, "") =>
            S3Key.of(bucketPrefix, id)
          case (a, _) =>
            if (a.isEmpty) S3Key.of(bucketPrefix, id)
            else S3Key.of(s"$bucketPrefix-$a", id)
        },
      _.startsWith(bucketPrefix)
    )
  }
}
