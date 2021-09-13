package binny.jdbc

import binny._
import binny.util.Logger
import cats.effect._
import munit.CatsEffectSuite

class JdbcAttributeStoreTest
    extends CatsEffectSuite
    with BinaryAttributeStoreAsserts
    with DbFixtures {
  implicit private val log: Logger[IO] = Log4sLogger[IO](org.log4s.getLogger)

  lazy val attrStore = h2AttrStore(Dbms.PostgreSQL, log, JdbcAttrConfig.default)

  val id = BinaryId("the-id-1")

  attrStore.test("insert attributes") { store =>
    for {
      attr <- ExampleData.helloWorldAttr
      _ <- store.saveAttr(id, IO(attr))
      att2 <- store.findAttr(id).value
      _ = assert(att2.isDefined)
      _ = assertEquals(att2, Some(attr))
    } yield ()
  }

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
