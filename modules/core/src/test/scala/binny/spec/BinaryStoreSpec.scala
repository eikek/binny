package binny.spec

import java.security.MessageDigest

import binny.Binary.Implicits._
import binny._
import binny.util.Stopwatch
import cats.effect.IO
import fs2.Stream
import munit.CatsEffectSuite

trait BinaryStoreSpec[S <: BinaryStore[IO]] { self: CatsEffectSuite =>

  val logger = Log4sLogger[IO](org.log4s.getLogger)
  val binStore: Fixture[S]

  val hint: Hint = Hint.none
  val md = MessageDigest.getInstance("SHA-256")
  def noFileError: Binary[IO] = sys.error("No binary found")

  test("insert and load") {
    val store = binStore()
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

  test("insert and load large file") {
    val store = binStore()
    (for {
      w <- Stream.eval(Stopwatch.start[IO])
      id <- ExampleData.file2M.through(store.insert(hint))
      bin <- Stream.eval(store.findBinary(id, ByteRange.All).getOrElse(noFileError))
      attr <- bin.computeAttributes(ContentTypeDetect.none, hint)
      _ = assertEquals(attr, ExampleData.file2MAttr)
      _ <- Stream.eval(Stopwatch.show(w)(d => logger.debug(s"Large file test took: $d")))
    } yield ()).compile.drain
  }

  test("load small range") {
    val store = binStore()
    (for {
      id <- ExampleData.helloWorld[IO].through(store.insert(hint))
      bin <- Stream.eval(store.findBinary(id, ByteRange(2, 5)).getOrElse(noFileError))
      str <- bin.readUtf8String
      _ = assertEquals(str, "llo W")
    } yield ()).compile.drain
  }

  test("delete") {
    val store = binStore()
    (for {
      id <- ExampleData.helloWorld[IO].through(store.insert(hint))
      _ <- Stream.eval(store.findBinary(id, ByteRange.All).getOrElse(noFileError))
      _ <- Stream.eval(store.delete(id))
      res <- Stream.eval(store.findBinary(id, ByteRange.All).value)
      _ = assertEquals(res, None)
    } yield ()).compile.drain
  }
}
