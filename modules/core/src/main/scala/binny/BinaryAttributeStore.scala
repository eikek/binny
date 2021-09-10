package binny

import cats.Applicative
import cats.data.OptionT

trait BinaryAttributeStore[F[_]] extends ReadonlyAttributeStore[F] {

  /** Associate the attributes to the key. If already exists, the data is replaced. */
  def saveAttr(id: BinaryId, attrs: BinaryAttributes): F[Unit]

  /** Removes the attributes associated to this id, if existing. */
  def deleteAttr(id: BinaryId): F[Boolean]

}

object BinaryAttributeStore {

  def empty[F[_]: Applicative]: BinaryAttributeStore[F] =
    new BinaryAttributeStore[F] {
      def findAttr(id: BinaryId): OptionT[F, BinaryAttributes] =
        OptionT.none[F, BinaryAttributes]

      def saveAttr(id: BinaryId, attrs: BinaryAttributes): F[Unit] =
        Applicative[F].pure(())

      def deleteAttr(id: BinaryId): F[Boolean] =
        Applicative[F].pure(false)
    }

}
