package binny.jdbc

import binny._
import cats.data.OptionT

/** Extends `BinaryStore` with some extra features. */
trait JdbcBinaryStore[F[_]] extends BinaryStore[F] {

  /** Same as `#findBinary()`, but uses a single connection for the entire byte stream.
    * Thus the connection is closed when the stream terminates. This is useful for small
    * files or when only a small portion of a file is requested.
    */
  def findBinaryStateful(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]]

  /** Computes the attributes of the given binary. Consider a `BinaryAttributeStore` when
    * frequently requesting this data. This is more efficient than using the
    * `computeAttributes` method on a binary, since it can utilise the database in a
    * better way.
    */
  def computeAttr(id: BinaryId, hint: Hint): OptionT[F, BinaryAttributes]
}
