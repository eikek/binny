package binny

import cats.effect.{IO, SyncIO}
import munit.CatsEffectSuite

abstract class BasicStoreSuite[S <: BinaryStore[IO]]
    extends CatsEffectSuite
    with BinaryStoreAsserts {

  val binStore: SyncIO[FunFixture[S]]

  binStore.test("insert and load") { store =>
    store.assertInsertAndLoad(ExampleData.helloWorld)
    store.assertInsertAndLoad(ExampleData.empty)
    store.assertInsertAndLoadLargerFile()
  }

  binStore.test("insert and load range") { store =>
    for {
      data <- store.insertAndLoadRange(ExampleData.helloWorld, ByteRange(2, 5))
      str  <- data.bytes.through(fs2.text.utf8.decode).foldMonoid.compile.lastOrError
      _ = assertEquals(str, "llo W")
    } yield ()
  }

  binStore.test("insert and delete") { store =>
    store.assertInsertAndDelete()
  }
}
