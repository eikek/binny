package binny.fs

import binny._
import binny.util.Logger
import cats.effect._
import cats.syntax.all._
import fs2.Stream
import fs2.concurrent.SignallingRef

/** A variant of [[FsBinaryStore]] that will cleanup empty directories that could be left
  * when deleting files.
  *
  * Since this deletion of directories could race against a concurrent inserting of a
  * file, both operations are run sequentially. With this class there should be only one
  * store working on the same base directory!
  */
final class FsBinaryStoreWithCleanup[F[_]: Async: Logger] private (
    val underlying: FsBinaryStore[F],
    private[fs] val state: SignallingRef[F, FsBinaryStoreWithCleanup.State]
) extends BinaryStore[F] {
  private val directoryRemove: EmptyDirectoryRemove[F] =
    new EmptyDirectoryRemove[F](underlying.config, Logger[F])

  def listIds(prefix: Option[String], chunkSize: Int) =
    underlying.listIds(prefix, chunkSize)

  def insert = in => Stream.resource(insertResource).flatMap(_ => underlying.insert(in))

  def insertWith(id: BinaryId) = in =>
    Stream.resource(insertResource).flatMap(_ => underlying.insertWith(id)(in))

  def findBinary(id: BinaryId, range: ByteRange) = underlying.findBinary(id, range)

  def exists(id: BinaryId) = underlying.exists(id)

  def delete(id: BinaryId) =
    deleteResource.use(_ => underlying.delete(id) *> directoryRemove.removeEmptyDirs(id))

  def computeAttr(id: BinaryId, hint: Hint) = underlying.computeAttr(id, hint)

  private def insertResource: Resource[F, Unit] =
    Resource.make(acquireInsert)(_ => state.update(_.decInsert))

  private def acquireInsert: F[Unit] =
    state.modify(_.incInsert).flatMap {
      case true  => ().pure[F]
      case false => state.waitUntil(_.noDeleteRunning) *> acquireInsert
    }

  private def deleteResource: Resource[F, Unit] =
    Resource.make(acquireDelete)(_ => state.update(_.decDelete))

  private def acquireDelete: F[Unit] =
    state.modify(_.incDelete).flatMap {
      case true  => ().pure[F]
      case false => state.waitUntil(_.noInsertRunning) *> acquireDelete
    }

  private[fs] def getState: F[FsBinaryStoreWithCleanup.State] =
    state.get
}

object FsBinaryStoreWithCleanup {

  case class State(insertsRunning: Long, deletionsRunning: Long) {
    def incInsert =
      if (isDeletionRunning) (this, false)
      else (State(insertsRunning + 1, deletionsRunning), true)

    def decInsert = State(insertsRunning - 1, deletionsRunning)
    def isInsertRunning = insertsRunning > 0
    def noInsertRunning = !isInsertRunning

    def incDelete =
      if (isInsertRunning) (this, false)
      else (copy(deletionsRunning = deletionsRunning + 1), true)
    def decDelete = copy(deletionsRunning = deletionsRunning - 1)
    def isDeletionRunning: Boolean = deletionsRunning > 0
    def noDeleteRunning: Boolean = !isDeletionRunning
  }

  object State {
    val empty: State = State(0, 0)
  }

  def apply[F[_]: Async: Logger](fs: FsBinaryStore[F]): F[FsBinaryStoreWithCleanup[F]] =
    SignallingRef.of(State.empty).map(new FsBinaryStoreWithCleanup(fs, _))

  def apply[F[_]: Async: Logger](
      config: FsStoreConfig
  ): F[FsBinaryStoreWithCleanup[F]] =
    apply(FsBinaryStore(config, Logger[F]))
}
