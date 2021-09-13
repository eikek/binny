package binny

import cats.effect.{IO, SyncIO}
import munit.CatsEffectSuite

abstract class BasicAttributeStoreSuite[S <: BinaryAttributeStore[IO]]
    extends CatsEffectSuite
    with BinaryAttributeStoreAsserts {

  val attrStore: SyncIO[FunFixture[S]]

  attrStore.test("save and find") { store =>
    for {
      attr <- ExampleData.helloWorldAttr
      _ <- store.assertInsertAndFind(attr)
    } yield ()
  }

  attrStore.test("save and delete") { store =>
    for {
      attr <- ExampleData.helloWorldAttr
      _ <- store.assertInsertAndDelete(attr)
    } yield ()
  }

  attrStore.test("insert twice") { store =>
    for {
      attr <- ExampleData.helloWorldAttr
      _ <- store.assertInsertTwice(attr)
    } yield ()
  }
}
