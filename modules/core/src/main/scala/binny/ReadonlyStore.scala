package binny

import cats.data.OptionT

trait ReadonlyStore[F[_]] {

  def load(id: BinaryId, range: ByteRange): OptionT[F, BinaryData[F]]

}
