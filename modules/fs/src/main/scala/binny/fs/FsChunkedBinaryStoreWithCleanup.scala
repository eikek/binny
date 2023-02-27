package binny.fs

import binny._
import binny.util.Logger
import cats.effect._
import cats.syntax.all._
import fs2.Stream
import scodec.bits.ByteVector

final class FsChunkedBinaryStoreWithCleanup[F[_]: Async: Logger] private (
    val underlying: FsChunkedBinaryStore[F],
    private[fs] val sync: InsertDeleteSync[F]
) extends ChunkedBinaryStore[F] {
  private val directoryRemove: EmptyDirectoryRemove[F] =
    EmptyDirectoryRemove[F](underlying.config)

  def listIds(prefix: Option[String], chunkSize: Int) =
    underlying.listIds(prefix, chunkSize)

  def insert = in =>
    Stream.eval(sync.insertResource.use(_ => underlying.insert(in).compile.lastOrError))

  def insertWith(id: BinaryId) = in =>
    Stream
      .eval(sync.insertResource.use(_ => underlying.insertWith(id)(in).compile.drain))
      .drain

  def insertChunk(
      id: BinaryId,
      chunkDef: ChunkDef,
      hint: Hint,
      data: ByteVector
  ): F[InsertChunkResult] =
    sync.insertResource.use(_ => underlying.insertChunk(id, chunkDef, hint, data))

  def findBinary(id: BinaryId, range: ByteRange) = underlying.findBinary(id, range)

  def exists(id: BinaryId) = underlying.exists(id)

  def delete(id: BinaryId) =
    sync.deleteResource.use(_ =>
      underlying.delete(id) *> directoryRemove.removeEmptyDirs(id)
    )

  def computeAttr(id: BinaryId, hint: Hint) = underlying.computeAttr(id, hint)
}

object FsChunkedBinaryStoreWithCleanup {

  def apply[F[_]: Async: Logger](
      fs: FsChunkedBinaryStore[F]
  ): F[FsChunkedBinaryStoreWithCleanup[F]] =
    InsertDeleteSync[F].map(new FsChunkedBinaryStoreWithCleanup[F](fs, _))

  def apply[F[_]: Async: Logger](
      config: FsChunkedStoreConfig
  ): F[FsChunkedBinaryStoreWithCleanup[F]] =
    apply(FsChunkedBinaryStore(Logger[F], config))
}
