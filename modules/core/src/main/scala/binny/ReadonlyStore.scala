package binny

import cats.data.OptionT

trait ReadonlyStore[F[_]] {

  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, BinaryData[F]]

}
