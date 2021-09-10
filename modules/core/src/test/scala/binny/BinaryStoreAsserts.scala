package binny

import binny.ContentTypeDetect.Hint
import cats.effect._
import fs2.{Chunk, Stream}
import munit.CatsEffectSuite

trait BinaryStoreAsserts { self: CatsEffectSuite =>

  implicit final class BinaryStoreOps(val bs: BinaryStore[IO]) {

    def assertInsertAndLoad(data: Stream[IO, Byte]): IO[Unit] =
      for {
        givenSha <- data.through(fs2.hash.sha256).chunks.head.compile.lastOrError
        id       <- bs.insert(data, Hint.none)
        elOpt    <- bs.load(id, ByteRange.All, 1024).value
        el = elOpt.getOrElse(sys.error("Binary not found"))
        elAttr <- el.computeAttributes(ContentTypeDetect.none, Hint.none)
        _ = self.assertEquals(elAttr.sha256, givenSha.toByteVector)
      } yield ()

    def insertAndLoadRange(data: Stream[IO, Byte], range: ByteRange): IO[BinaryData[IO]] =
      for {
        id    <- bs.insert(data, Hint.none)
        elOpt <- bs.load(id, range, 1024).value
        el = elOpt.getOrElse(sys.error("Binary not found"))
      } yield el

    def assertInsertAndLoadLargerFile(): IO[Unit] = {
      val data = Stream
        .emit("abcdefghijklmnopqrstuvwxyz")
        .repeat
        .take(20000)
        .flatMap(str => Stream.chunk(Chunk.array(str.getBytes)))
        .covary[IO]
        .buffer(32 * 1024)

      for {
        givenSha <- data.through(fs2.hash.sha256).chunks.head.compile.lastOrError
        id       <- bs.insert(data, Hint.none)
        elOpt    <- bs.load(id, ByteRange.All, 16 * 1024).value
        el = elOpt.getOrElse(sys.error("Binary not found"))
        elAttr <- el.computeAttributes(ContentTypeDetect.none, Hint.none)
        _ = self.assertEquals(elAttr.sha256, givenSha.toByteVector)
        _ = println(s"${elAttr.sha256.toHex}")
      } yield ()
    }

    def assertInsertAndDelete(): IO[Unit] =
      for {
        id   <- bs.insert(ExampleData.helloWorld[IO], Hint.none)
        file <- bs.load(id, ByteRange.All, 1024).value
        _ = assert(file.isDefined)
        deleted <- bs.delete(id)
        _ = assert(deleted)
        file2 <- bs.load(id, ByteRange.All, 1024).value
        _ = assert(file2.isEmpty)
      } yield ()
  }

}
