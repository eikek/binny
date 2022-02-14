package binny.jdbc

import binny._
import cats.data.OptionT
import fs2.Pipe

final class SwapFind[F[_]](delegate: JdbcBinaryStore[F]) extends BinaryStore[F] {

  def insert(hint: Hint): Pipe[F, Byte, BinaryId] =
    delegate.insert(hint)

  def insertWith(id: BinaryId, hint: Hint): Pipe[F, Byte, Nothing] =
    delegate.insertWith(id, hint)

  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]] =
    delegate.findBinaryStateful(id, range)

  def delete(id: BinaryId): F[Unit] =
    delegate.delete(id)

  def listIds(prefix: Option[String], chunkSize: Int) =
    delegate.listIds(prefix, chunkSize)
}

object SwapFind {
  def apply[F[_]](delegate: JdbcBinaryStore[F]): BinaryStore[F] =
    new SwapFind[F](delegate)
}
