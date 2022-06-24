import java.security.MessageDigest

import cats.data.{Kleisli, NonEmptySet, OptionT}
import cats.{Applicative, ApplicativeError, Functor}
import fs2.{Chunk, Pipe, Stream}
import scodec.bits.ByteVector

package object binny {

  type Binary[F[_]] = Stream[F, Byte]

  object Binary {
    def empty[F[_]]: Binary[F] =
      Stream.empty.covary[F]

    def utf8String[F[_]](content: String): Binary[F] =
      Stream.emit(content).through(fs2.text.utf8.encode)

    def byteVector[F[_]](bv: ByteVector): Binary[F] =
      Stream.chunk(Chunk.byteVector(bv))
  }

  type AttributeNameSet = NonEmptySet[AttributeName]

  /** A function to compute attributes to a binary. It is possible to specify what
    * information to request, so implementations can skip potentially expensive calls.
    */
  type ComputeAttr[F[_]] = Kleisli[OptionT[F, *], AttributeNameSet, BinaryAttributes]

  object ComputeAttr {
    def pure[F[_]: Applicative](attr: BinaryAttributes): ComputeAttr[F] =
      Kleisli(_ => OptionT.some[F](attr))

    def liftF[F[_]: Functor](fb: F[BinaryAttributes]): ComputeAttr[F] =
      Kleisli(_ => OptionT.liftF(fb))

    def raiseError[F[_]](ex: Throwable)(implicit
        F: ApplicativeError[F, Throwable]
    ): ComputeAttr[F] =
      Kleisli(_ => OptionT.liftF(F.raiseError[BinaryAttributes](ex)))

    def computeAll[F[_]](
        ct: ContentTypeDetect,
        hint: Hint
    ): Pipe[F, Byte, BinaryAttributes] =
      in =>
        Stream.suspend {
          in.chunks.fold(AttrState.empty)(_.update(ct, hint)(_)).map(_.toAttributes)
        }

    def computeNoSha256[F[_]](
        ct: ContentTypeDetect,
        hint: Hint
    ): Pipe[F, Byte, BinaryAttributes] =
      in =>
        Stream.suspend {
          in.chunks.fold(AttrState.empty)(_.updateNoSha(ct, hint)(_)).map(_.toAttributes)
        }

    final private[binny] case class AttrState(
        md: MessageDigest,
        len: Long,
        ct: Option[SimpleContentType]
    ) {
      def updateNoSha(detect: ContentTypeDetect, hint: Hint)(c: Chunk[Byte]): AttrState =
        AttrState(md, len + c.size, ct.orElse(Some(detect.detect(c.toByteVector, hint))))

      def update(detect: ContentTypeDetect, hint: Hint)(c: Chunk[Byte]): AttrState = {
        val bytes = c.toArraySlice
        val bv = ByteVector.view(bytes.values, bytes.offset, bytes.size)
        md.update(bytes.values, bytes.offset, bytes.size)
        AttrState(md, len + c.size, ct.orElse(Some(detect.detect(bv, hint))))
      }

      def update(
          detect: ContentTypeDetect,
          hint: Hint,
          c: Array[Byte],
          len: Int
      ): AttrState = {
        md.update(c, 0, len)
        AttrState(
          md,
          len + c.length,
          ct.orElse(Some(detect.detect(ByteVector.view(c), hint)))
        )
      }

      def toAttributes: BinaryAttributes =
        BinaryAttributes(
          ByteVector.view(md.digest()),
          ct.getOrElse(SimpleContentType.octetStream),
          len
        )
    }
    private[binny] object AttrState {
      def empty = AttrState(MessageDigest.getInstance("SHA-256"), 0, None)
    }
  }
}
