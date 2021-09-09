package binny

import cats.Applicative
import cats.data.OptionT

trait BinaryAttributeStore[F[_]] extends ReadonlyAttributeStore[F] {

  def saveAttr(id: BinaryId, attrs: BinaryAttributes): F[Unit]

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
