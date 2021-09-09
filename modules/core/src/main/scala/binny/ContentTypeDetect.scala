package binny

import fs2.Stream
import scodec.bits.ByteVector

trait ContentTypeDetect {

  def detect(data: ByteVector, hint: ContentTypeDetect.Hint): SimpleContentType

  def detectStream[F[_]](
      data: Stream[F, Byte],
      hint: ContentTypeDetect.Hint
  ): F[SimpleContentType] =
    ???
}

object ContentTypeDetect {
  val none = ContentTypeDetect((_, _) => SimpleContentType.octetStream)

  def apply(f: (ByteVector, Hint) => SimpleContentType): ContentTypeDetect =
    new ContentTypeDetect {
      def detect(data: ByteVector, hint: Hint) = f(data, hint)
    }

  final case class Hint(filename: Option[String], advertisedType: Option[String]) {
    def withAdvertised(contentType: String): Hint =
      copy(advertisedType = Option(contentType).filter(_.nonEmpty))

    def withFilename(filename: String): Hint =
      copy(filename = Option(filename).filter(_.nonEmpty))
  }

  object Hint {
    val none = Hint(None, None)

    def filename(fn: String): Hint =
      none.withFilename(fn)

    def advertised(contentType: String): Hint =
      none.withAdvertised(contentType)

    def apply(fn: String, advertised: String): Hint =
      filename(fn).withAdvertised(advertised)
  }
}
