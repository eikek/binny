package binny

import binny.ContentTypeDetect.Hint
import fs2.{Compiler, Stream}

final case class BinaryData[F[_]](id: BinaryId, bytes: Stream[F, Byte]) {

  def computeAttributes(detect: ContentTypeDetect, hint: Hint)(implicit
      F: Compiler.Target[F]
  ): F[BinaryAttributes] =
    bytes.through(BinaryAttributes.compute(detect, hint)).compile.lastOrError
}
