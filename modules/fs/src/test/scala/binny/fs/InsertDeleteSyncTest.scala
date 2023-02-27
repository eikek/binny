package binny.fs

import scala.concurrent.duration._

import binny.fs.InsertDeleteSyncTest.TestStore
import binny.util.Logger
import cats.effect.IO
import cats.effect.kernel.Ref
import fs2.Stream
import munit.CatsEffectSuite

class InsertDeleteSyncTest extends CatsEffectSuite {
  implicit val logger: Logger[IO] = Logger.silent[IO]

  def createStore: IO[TestStore] =
    InsertDeleteSync[IO].map(new TestStore(_))

  test("insert and delete concurrently") {
    for {
      // prepare
      store <- createStore

      // half insert / half delete
      (ins, del) = (15, 15)
      inserts = Stream
        .repeatEval(store.insert)
        .take(ins)

      delete = Stream
        .repeatEval(store.delete)
        .take(del)

      // state invariant
      cond = store.state.continuous.evalMap { state =>
        val ok = state.noDeleteRunning || state.noInsertRunning
        if (!ok)
          IO.raiseError(
            new Exception(s"Invalid state during operation: $state")
          )
        else IO.pure(ok)
      }.drain

      // run all
      _ <- cond
        .mergeHaltR(Stream.emits(List(inserts, delete)).parJoinUnbounded)
        .compile
        .drain

      // assertions
      state <- store.state.get
      _ = assert(state.noInsertRunning && state.noDeleteRunning)
      _ <- assertIO(store.ref.get, (0, 0))
    } yield ()
  }
}

object InsertDeleteSyncTest {

  class TestStore(sync: InsertDeleteSync[IO]) {

    val state = sync.state
    val ref = Ref.unsafe[IO, (Int, Int)]((0, 0))

    def insert: IO[Unit] =
      sync.insertResource.use { _ =>
        for {
          _ <- changeInsert(_ + 1)
          _ <- IO.sleep(10.millis)
          _ <- changeInsert(_ - 1)
        } yield ()
      }

    def delete: IO[Unit] =
      sync.deleteResource.use { _ =>
        for {
          _ <- changeDelete(_ + 1)
          _ <- IO.sleep(10.millis)
          _ <- changeDelete(_ - 1)
        } yield ()
      }

    def changeInsert(f: Int => Int) =
      ref.getAndUpdate { case (ins, del) =>
        if (del > 0) sys.error("Delete while insert")
        else (f(ins), 0)
      }.void

    def changeDelete(f: Int => Int) =
      ref.getAndUpdate { case (ins, del) =>
        if (ins > 0) sys.error("Insert while delete")
        else (0, f(del))
      }.void
  }
}
