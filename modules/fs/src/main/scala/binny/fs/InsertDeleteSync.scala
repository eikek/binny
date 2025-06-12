package binny.fs

import cats.effect._
import cats.syntax.all._
import fs2.concurrent.SignallingRef

final private[fs] class InsertDeleteSync[F[_]: Async](
    val state: SignallingRef[F, InsertDeleteSync.State]
) {

  def insertResource: Resource[F, Unit] =
    Resource.make(acquireInsert)(_ => state.update(_.decInsert))

  private def acquireInsert: F[Unit] =
    state.modify(_.incInsert).flatMap {
      case true  => ().pure[F]
      case false => state.waitUntil(_.noDeleteRunning) *> acquireInsert
    }

  def deleteResource: Resource[F, Unit] =
    Resource.make(acquireDelete)(_ => state.update(_.decDelete))

  private def acquireDelete: F[Unit] =
    state.modify(_.incDelete).flatMap {
      case true  => ().pure[F]
      case false =>
        state.waitUntil(_.noInsertRunning) *> acquireDelete
    }

}

private[fs] object InsertDeleteSync {

  def apply[F[_]: Async]: F[InsertDeleteSync[F]] =
    SignallingRef.of[F, State](State.empty).map(new InsertDeleteSync[F](_))

  final case class State(insertsRunning: Long, deletionsRunning: Long) {
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
}
