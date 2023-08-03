package binny.fs

import binny._
import binny.util.Logger
import cats.effect._
import cats.syntax.all._
import fs2.Stream
import fs2.io.file.Files

/** A variant of [[FsBinaryStore]] that will cleanup empty directories that could be left
  * when deleting files.
  *
  * Since this deletion of directories could race against a concurrent inserting of a
  * file, both operations are run sequentially. With this class there should be only one
  * store working on the same base directory!
  */
final class FsBinaryStoreWithCleanup[F[_]: Async: Files: Logger] private (
    val underlying: FsBinaryStore[F],
    private[fs] val sync: InsertDeleteSync[F]
) extends BinaryStore[F] {
  private val directoryRemove: EmptyDirectoryRemove[F] =
    EmptyDirectoryRemove[F](underlying.config)

  private[fs] val state = sync.state

  def listIds(prefix: Option[String], chunkSize: Int) =
    underlying.listIds(prefix, chunkSize)

  def insert = in =>
    Stream.eval(sync.insertResource.use(_ => underlying.insert(in).compile.lastOrError))

  def insertWith(id: BinaryId) = in =>
    Stream
      .eval(sync.insertResource.use(_ => underlying.insertWith(id)(in).compile.drain))
      .drain

  def findBinary(id: BinaryId, range: ByteRange) = underlying.findBinary(id, range)

  def exists(id: BinaryId) = underlying.exists(id)

  def delete(id: BinaryId) =
    sync.deleteResource.use(_ =>
      underlying.delete(id) *> directoryRemove.removeEmptyDirs(id)
    )

  def computeAttr(id: BinaryId, hint: Hint) = underlying.computeAttr(id, hint)
}

object FsBinaryStoreWithCleanup {

  def apply[F[_]: Async: Files: Logger](
      fs: FsBinaryStore[F]
  ): F[FsBinaryStoreWithCleanup[F]] =
    InsertDeleteSync[F].map(new FsBinaryStoreWithCleanup(fs, _))

  def apply[F[_]: Async: Files: Logger](
      config: FsStoreConfig
  ): F[FsBinaryStoreWithCleanup[F]] =
    apply(FsBinaryStore(config, Logger[F]))
}
