import java.security.MessageDigest

import fs2.{Chunk, Pipe, Stream}
import scodec.bits.ByteVector

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
      implicit final class BinaryOps[F[_]](private val self: Binary[F]) extends AnyVal {
        def computeAttributes(
            detect: ContentTypeDetect,
            hint: Hint
        ): Stream[F, BinaryAttributes] =
          self.through(Binary.computeAttributes(detect, hint))

        def messageDigest(md: => MessageDigest): Stream[F, Byte] =
          self.through(fs2.hash.digest(md))

        def readUtf8String[G[_]](implicit F: fs2.Compiler[F, G]): G[String] =
          self.through(fs2.text.utf8.decode).compile.string
      }
    }
  }
}
