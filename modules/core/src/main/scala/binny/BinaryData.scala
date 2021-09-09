package binny

import binny.ContentTypeDetect.Hint
import cats.effect.kernel.Sync
import fs2.Stream

final case class BinaryData[F[_]](id: BinaryId, bytes: Stream[F, Byte]) {

  def computeAttributes(detect: ContentTypeDetect, hint: Hint)(implicit
      F: Sync[F]
  ): F[BinaryAttributes] =
    bytes.through(BinaryAttributes.compute(detect, hint)).compile.lastOrError
}
