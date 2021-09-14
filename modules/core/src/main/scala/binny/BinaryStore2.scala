package binny

import binny.ContentTypeDetect.Hint
import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import fs2.{Pipe, Stream}

trait BinaryStore2[F[_]] extends ReadonlyAttributeStore[F] {

  /** Insert the given bytes creating a new id. */
  def insert(hint: Hint): Pipe[F, Byte, BinaryId]

  /** Insert the given bytes to the given id. If some file already exists by this id, the
    * behavior depends on the implementation.
    */
  def insertWith(id: BinaryId, hint: Hint): Pipe[F, Byte, Nothing]

  /** Finds a binary by its id. The range argument controls which part to return. */
  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]]

  /** Deletes all data associated to the given id. */
  def delete(id: BinaryId): F[Unit]
}

object BinaryStore2 {
  def none[F[_]: Sync]: BinaryStore2[F] =
    new BinaryStore2[F] {
      def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]] =
        OptionT.none

      def insert(hint: Hint): Pipe[F, Byte, BinaryId] =
        _ => Stream.eval(BinaryId.random[F])

      def insertWith(id: BinaryId, hint: Hint): Pipe[F, Byte, Nothing] =
        _ => Stream.empty

      def delete(id: BinaryId): F[Unit] =
        ().pure[F]

      def findAttr(id: BinaryId): OptionT[F, BinaryAttributes] =
        OptionT.none
    }
}
