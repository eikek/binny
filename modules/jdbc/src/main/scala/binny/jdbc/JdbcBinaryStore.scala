package binny.jdbc

import binny._
import cats.data.OptionT

trait JdbcBinaryStore[F[_]] extends BinaryStore[F] {

  /** Same as [[findBinary()]], but uses a single connection for the entire byte stream.
    * Thus the connection is closed when the stream terminates. This is useful for small
    * files or when only a small portion of a file is requested.
    */
  def findBinaryStateful(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]]

}
