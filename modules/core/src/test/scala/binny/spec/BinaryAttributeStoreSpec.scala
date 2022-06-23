package binny.spec

import binny._
import cats.effect.IO
import munit.CatsEffectSuite

abstract class BinaryAttributeStoreSpec[S <: BinaryAttributeStore[IO]]
    extends CatsEffectSuite {

  def attrStore: Fixture[S]

  test("attributes: save and find") {
    val store = attrStore()
    for {
      attr <- ExampleData.helloWorldAttr
      id <- BinaryId.random[IO]
      none <- store.findAttr(id).value
      _ = assert(none.isEmpty)
      _ <- store.saveAttr(id, ComputeAttr.pure(attr))
      a <- store.findAttr(id).value
      _ = assertEquals(a, Some(attr))

    } yield ()
  }

  test("attributes: save and delete") {
    val store = attrStore()
    for {
      attr <- ExampleData.helloWorldAttr
      id <- BinaryId.random[IO]
      _ <- store.saveAttr(id, ComputeAttr.pure(attr))
      a <- store.findAttr(id).value
      _ = assertEquals(a, Some(attr))
      _ <- store.deleteAttr(id)
      b <- store.findAttr(id).value
      _ = assert(b.isEmpty)
    } yield ()
  }

  test("attributes: insert twice") {
    val store = attrStore()
    for {
      attr <- ExampleData.helloWorldAttr
      id <- BinaryId.random[IO]
      _ <- store.saveAttr(id, ComputeAttr.pure(attr))
      attr2 = attr.copy(contentType = SimpleContentType("application/json"))
      _ <- store.saveAttr(id, ComputeAttr.pure(attr2))
      a <- store.findAttr(id).value
      _ = assertEquals(a, Some(attr2))
    } yield ()
  }
}
