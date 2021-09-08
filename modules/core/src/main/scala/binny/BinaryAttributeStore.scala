package binny

import cats.data.OptionT

trait BinaryAttributeStore[F[_]] {

  def insert(id: BinaryId, attrs: BinaryAttributes): F[Unit]

  def find(id: BinaryId): OptionT[F, BinaryAttributes]
}

object BinaryAttributeStore {


}
