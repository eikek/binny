package binny.spec

import binny.Binary.Implicits._
import binny.ContentTypeDetect.Hint
import binny.{Binary, BinaryStore2, ByteRange, ContentTypeDetect, ExampleData}
import cats.effect.{IO, SyncIO}
import fs2.Stream
import munit.CatsEffectSuite

import java.security.MessageDigest

abstract class BinaryStoreSpec[S <: BinaryStore2[IO]]
    extends CatsEffectSuite {

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
      id <- ExampleData.file2M.through(store.insert(hint))
      bin <- Stream.eval(store.findBinary(id, ByteRange.All).getOrElse(noFileError))
      attr <- bin.computeAttributes(ContentTypeDetect.none, hint)
      _ = assertEquals(attr, ExampleData.file2MAttr)
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
