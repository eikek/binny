package binny

import cats.data.OptionT
import cats.effect.Sync
import cats.implicits._
import fs2.{Pipe, Stream}

trait BinaryStore[F[_]] {

  /** Returns a set of ids currently available in this store. */
  def listIds(prefix: Option[String], chunkSize: Int): Stream[F, BinaryId]

  /** Insert the given bytes creating a new id. */
  def insert(hint: Hint): Pipe[F, Byte, BinaryId]

  /** Insert the given bytes to the given id. If some file already exists by this id, the
    * behavior depends on the implementation.
    */
  def insertWith(id: BinaryId, hint: Hint): Pipe[F, Byte, Nothing]

  /** Finds a binary by its id. The range argument controls which part to return. */
  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]]

  /** Check if a file exists. Same as `findBinary().isDefined`, but usually more
    * efficiently implemented.
    */
  def exists(id: BinaryId): F[Boolean]

  /** Deletes all data associated to the given id. */
  def delete(id: BinaryId): F[Unit]
}

object BinaryStore {
  def none[F[_]: Sync]: BinaryStore[F] =
    new BinaryStore[F] {
      def listIds(prefix: Option[String], chunkSize: Int): Stream[F, BinaryId] =
        Stream.empty

      def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]] =
        OptionT.none

      def exists(id: BinaryId) = false.pure[F]

      def insert(hint: Hint): Pipe[F, Byte, BinaryId] =
        _ => Stream.eval(BinaryId.random[F])

      def insertWith(id: BinaryId, hint: Hint): Pipe[F, Byte, Nothing] =
        _ => Stream.empty

      def delete(id: BinaryId): F[Unit] =
        ().pure[F]
    }
}
