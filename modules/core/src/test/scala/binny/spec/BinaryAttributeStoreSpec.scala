package binny.spec

import binny._
import cats.effect.{IO, SyncIO}
import munit.CatsEffectSuite

abstract class BinaryAttributeStoreSpec[S <: BinaryAttributeStore[IO]]
    extends CatsEffectSuite {

  val attrStore: SyncIO[FunFixture[S]]

  attrStore.test("save and find") { store =>
    for {
      attr <- ExampleData.helloWorldAttr
      id <- BinaryId.random[IO]
      none <- store.findAttr(id).value
      _ = assert(none.isEmpty)
      _ <- store.saveAttr(id, IO(attr))
      a <- store.findAttr(id).value
      _ = assertEquals(a, Some(attr))

    } yield ()
  }

  attrStore.test("save and delete") { store =>
    for {
      attr <- ExampleData.helloWorldAttr
      id <- BinaryId.random[IO]
      _ <- store.saveAttr(id, IO(attr))
      a <- store.findAttr(id).value
      _ = assertEquals(a, Some(attr))
      _ <- store.deleteAttr(id)
      b <- store.findAttr(id).value
      _ = assert(b.isEmpty)
    } yield ()
  }

  attrStore.test("insert twice") { store =>
    for {
      attr <- ExampleData.helloWorldAttr
      id <- BinaryId.random[IO]
      _ <- store.saveAttr(id, IO(attr))
      attr2 = attr.copy(contentType = SimpleContentType("application/json"))
      _ <- store.saveAttr(id, IO(attr2))
      a <- store.findAttr(id).value
      _ = assertEquals(a, Some(attr2))
    } yield ()
  }
}
