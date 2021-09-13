package binny

import binny.ContentTypeDetect.Hint
import binny.util.Stopwatch
import cats.effect._
import fs2.{Chunk, Stream}
import munit.CatsEffectSuite

trait BinaryStoreAsserts { self: CatsEffectSuite =>

  implicit final class BinaryStoreOps(val bs: BinaryStore[IO]) {

    def assertInsertAndLoad(data: Stream[IO, Byte]): IO[Unit] =
      for {
        givenSha <- data.through(fs2.hash.sha256).chunks.head.compile.lastOrError
        id <- bs.insert(data, Hint.none)
        elOpt <- bs.findBinary(id, ByteRange.All).value
        el = elOpt.getOrElse(sys.error("Binary not found"))
        elAttr <- el.computeAttributes(ContentTypeDetect.none, Hint.none)
        _ = self.assertEquals(elAttr.sha256, givenSha.toByteVector)
      } yield ()

    def insertAndLoadRange(data: Stream[IO, Byte], range: ByteRange): IO[BinaryData[IO]] =
      for {
        id <- bs.insert(data, Hint.none)
        elOpt <- bs.findBinary(id, range).value
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
      val log = Log4sLogger[IO](org.log4s.getLogger)
      for {
        givenSha <- data.through(fs2.hash.sha256).chunks.head.compile.lastOrError
        id <- Stopwatch.wrap(d => log.debug(s"Insert larger file took: $d")) {
          bs.insert(data, Hint.none)
        }
        w <- Stopwatch.start[IO]
        elOpt <- bs.findBinary(id, ByteRange.All).value
        el = elOpt.getOrElse(sys.error("Binary not found"))
        elAttr <- el.computeAttributes(ContentTypeDetect.none, Hint.none)
        _ <- Stopwatch.show(w)(d => log.debug(s"Loading and sha256 took: $d"))
        _ = self.assertEquals(elAttr.sha256, givenSha.toByteVector)
      } yield ()
    }

    def assertInsertAndDelete(): IO[Unit] =
      for {
        id <- bs.insert(ExampleData.helloWorld[IO], Hint.none)
        file <- bs.findBinary(id, ByteRange.All).value
        _ = assert(file.isDefined)
        deleted <- bs.delete(id)
        _ = assert(deleted)
        file2 <- bs.findBinary(id, ByteRange.All).value
        _ = assert(file2.isEmpty)
      } yield ()
  }

}
