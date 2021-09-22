package binny

import cats.Applicative
import cats.data.OptionT

trait BinaryAttributeStore[F[_]] {

  /** Associate the attributes to the key. If already exists, the data is replaced. */
  def saveAttr(id: BinaryId, attrs: F[BinaryAttributes]): F[Unit]

  /** Removes the attributes associated to this id, if existing. */
  def deleteAttr(id: BinaryId): F[Boolean]

  /** Looks up attributes for the given id. */
  def findAttr(id: BinaryId): OptionT[F, BinaryAttributes]
}

object BinaryAttributeStore {
  def empty[F[_]: Applicative]: BinaryAttributeStore[F] =
    new BinaryAttributeStore[F] {
      def findAttr(id: BinaryId): OptionT[F, BinaryAttributes] =
        OptionT.none[F, BinaryAttributes]

      def saveAttr(id: BinaryId, attrs: F[BinaryAttributes]): F[Unit] =
        Applicative[F].pure(())

      def deleteAttr(id: BinaryId): F[Boolean] =
        Applicative[F].pure(false)
    }
}
