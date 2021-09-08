package binny

import fs2.Stream


final case class BinaryData[F[_]](id: BinaryId, data: Stream[F, Byte])
