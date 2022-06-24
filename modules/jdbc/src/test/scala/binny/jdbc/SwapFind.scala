package binny.jdbc

import binny._
import cats.data.OptionT
import fs2.Pipe

final class SwapFind[F[_]](delegate: JdbcBinaryStore[F]) extends BinaryStore[F] {

  def insert: Pipe[F, Byte, BinaryId] =
    delegate.insert

  def insertWith(id: BinaryId): Pipe[F, Byte, Nothing] =
    delegate.insertWith(id)

  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]] =
    delegate.findBinaryStateful(id, range)

  def exists(id: BinaryId) =
    delegate.exists(id)

  def delete(id: BinaryId): F[Unit] =
    delegate.delete(id)

  def listIds(prefix: Option[String], chunkSize: Int) =
    delegate.listIds(prefix, chunkSize)

  def computeAttr(id: BinaryId, hint: Hint) = delegate.computeAttr(id, hint)
}

object SwapFind {
  def apply[F[_]](delegate: JdbcBinaryStore[F]): BinaryStore[F] =
    new SwapFind[F](delegate)
}
