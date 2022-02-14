package binny.minio

import binny._

trait S3KeyMapping {

  /** A bucket name must be deduced from a binary-id. */
  def toBucket(id: BinaryId): String

  /** When listing objects, it must be known in which buckets to look. */
  def bucketFilter(bucket: String): Boolean
}

object S3KeyMapping {

  def apply(
      f: BinaryId => String,
      filter: String => Boolean
  ): S3KeyMapping =
    new S3KeyMapping {
      def toBucket(id: BinaryId) = f(id)
      def bucketFilter(bucket: String) = filter(bucket)
    }

  /** Puts everything into a single bucket. */
  def constant(bucket: String): S3KeyMapping =
    S3KeyMapping(_ => bucket, _ == bucket)

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
            bucketPrefix
          case (a, _) =>
            if (a.isEmpty) bucketPrefix
            else s"$bucketPrefix-$a"
        },
      _.startsWith(bucketPrefix)
    )
  }
}
