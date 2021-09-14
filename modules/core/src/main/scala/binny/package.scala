import binny.ContentTypeDetect.Hint
import fs2.{Chunk, Pipe, Stream}
import scodec.bits.ByteVector

import java.security.MessageDigest

package object binny {

  type Binary[F[_]] = Stream[F, Byte]

  object Binary {

    /** Consumes the input stream and produces a single element stream containing the
      * attributes.
      */
    def computeAttributes[F[_]](
        detect: ContentTypeDetect,
        hint: Hint
    ): Pipe[F, Byte, BinaryAttributes] =
      _.through(BinaryAttributes.compute(detect, hint))

    def empty[F[_]]: Binary[F] =
      Stream.empty.covary[F]

    def utf8String[F[_]](content: String): Binary[F] =
      Stream.emit(content).through(fs2.text.utf8.encode)

    def byteVector[F[_]](bv: ByteVector): Binary[F] =
      Stream.chunk(Chunk.byteVector(bv))

    object Implicits {
      implicit final class BinaryOps[F[_]](bin: Binary[F]) {
        def computeAttributes(
            detect: ContentTypeDetect,
            hint: Hint
        ): Stream[F, BinaryAttributes] =
          bin.through(Binary.computeAttributes(detect, hint))

        def messageDigest(md: => MessageDigest): Stream[F, Byte] =
          bin.through(fs2.hash.digest(md))

        def readUtf8String: Stream[F, String] =
          bin.through(fs2.text.utf8.decode).foldMonoid
      }
    }
  }
}
