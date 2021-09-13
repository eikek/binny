package binny

import binny.ContentTypeDetect.Hint
import fs2.{Compiler, Stream}

final case class BinaryData[F[_]](id: BinaryId, bytes: Stream[F, Byte]) {

  def computeAttributes(detect: ContentTypeDetect, hint: Hint)(implicit
      F: Compiler.Target[F]
  ): F[BinaryAttributes] =
    bytes.through(BinaryAttributes.compute(detect, hint)).compile.lastOrError

  def readUtf8String: Stream[F, String] =
    bytes.through(fs2.text.utf8.decode).foldMonoid
}

object BinaryData {

  def utf8String[F[_]](id: BinaryId, content: String): BinaryData[F] =
    BinaryData(id, Stream.emit(content).through(fs2.text.utf8.encode))
}
