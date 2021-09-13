package binny

import cats.effect.IO
import munit.CatsEffectSuite

trait BinaryAttributeStoreAsserts { self: CatsEffectSuite =>

  implicit final class BinaryAttributeStoreOps(val bs: BinaryAttributeStore[IO]) {

    def assertInsertAndFind(attr: BinaryAttributes): IO[Unit] =
      for {
        id <- BinaryId.random[IO]
        none <- bs.findAttr(id).value
        _ = assert(none.isEmpty)
        _ <- bs.saveAttr(id, IO(attr))
        a <- bs.findAttr(id).value
        _ = assertEquals(a, Some(attr))
      } yield ()

    def assertInsertAndDelete(attr: BinaryAttributes): IO[Unit] =
      for {
        id <- BinaryId.random[IO]
        _ <- bs.saveAttr(id, IO(attr))
        a <- bs.findAttr(id).value
        _ = assertEquals(a, Some(attr))
        _ <- bs.deleteAttr(id)
        b <- bs.findAttr(id).value
        _ = assert(b.isEmpty)
      } yield ()

    def assertInsertTwice(attr: BinaryAttributes): IO[Unit] =
      for {
        id <- BinaryId.random[IO]
        _ <- bs.saveAttr(id, IO(attr))
        attr2 = attr.copy(contentType = SimpleContentType("application/json"))
        _ <- bs.saveAttr(id, IO(attr2))
        a <- bs.findAttr(id).value
        _ = assertEquals(a, Some(attr2))
      } yield ()
  }

}
