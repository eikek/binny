package binny

import cats.Applicative
import cats.data.OptionT

trait ReadonlyAttributeStore[F[_]] {

  def findAttr(id: BinaryId): OptionT[F, BinaryAttributes]

}

object ReadonlyAttributeStore {

  def empty[F[_]: Applicative]: ReadonlyAttributeStore[F] =
    (_: BinaryId) => OptionT.none[F, BinaryAttributes]
}
