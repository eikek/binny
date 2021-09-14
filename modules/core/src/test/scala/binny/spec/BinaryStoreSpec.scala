package binny.spec

import java.security.MessageDigest

import binny.Binary.Implicits._
import binny.ContentTypeDetect.Hint
import binny._
import binny.util.Stopwatch
import cats.effect.{IO, SyncIO}
import fs2.Stream
import munit.CatsEffectSuite

abstract class BinaryStoreSpec[S <: BinaryStore2[IO]] extends CatsEffectSuite {

  private[this] val logger = Log4sLogger[IO](org.log4s.getLogger)
  val binStore: SyncIO[FunFixture[S]]

  val hint: Hint = Hint.none
  val md = MessageDigest.getInstance("SHA-256")
  def noFileError: Binary[IO] = sys.error("No binary found")

  binStore.test("insert and load") { store =>
    Stream(ExampleData.helloWorld, Binary.empty, ExampleData.file2M.take(2203))
      .flatMap(data =>
        for {
          id <- data.through(store.insert(hint))
          sha <- data.messageDigest(md).chunks.head
          bin <- Stream.eval(
            store
              .findBinary(id, ByteRange.All)
              .getOrElse(noFileError)
          )
          shaEl <- bin.messageDigest(md).chunks.head
          _ = assertEquals(shaEl, sha)
        } yield ()
      )
      .compile
      .drain
  }

  binStore.test("insert and load large file") { store =>
    (for {
      w <- Stream.eval(Stopwatch.start[IO])
      id <- ExampleData.file2M.through(store.insert(hint))
      bin <- Stream.eval(store.findBinary(id, ByteRange.All).getOrElse(noFileError))
      attr <- bin.computeAttributes(ContentTypeDetect.none, hint)
      _ = assertEquals(attr, ExampleData.file2MAttr)
      _ <- Stream.eval(Stopwatch.show(w)(d => logger.debug(s"Large file test took: $d")))
    } yield ()).compile.drain
  }

  binStore.test("load small range") { store =>
    (for {
      id <- ExampleData.helloWorld[IO].through(store.insert(hint))
      bin <- Stream.eval(store.findBinary(id, ByteRange(2, 5)).getOrElse(noFileError))
      str <- bin.readUtf8String
      _ = assertEquals(str, "llo W")
    } yield ()).compile.drain
  }

  binStore.test("delete") { store =>
    (for {
      id <- ExampleData.helloWorld[IO].through(store.insert(hint))
      _ <- Stream.eval(store.findBinary(id, ByteRange.All).getOrElse(noFileError))
      _ <- Stream.eval(store.delete(id))
      res <- Stream.eval(store.findBinary(id, ByteRange.All).value)
      _ = assertEquals(res, None)
    } yield ()).compile.drain
  }
}
