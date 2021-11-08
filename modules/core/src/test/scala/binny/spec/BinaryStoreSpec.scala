package binny.spec

import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicInteger

import binny.Binary.Implicits._
import binny._
import binny.util.{Logger, Stopwatch}
import cats.effect.IO
import fs2.{Chunk, Stream}
import munit.CatsEffectSuite
import scodec.bits.ByteVector

abstract class BinaryStoreSpec[S <: BinaryStore[IO]] extends CatsEffectSuite {
  private[this] val logger = Logger.stdout[IO](Logger.Level.Warn, getClass.getSimpleName)

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
      str <- Stream.eval(bin.readUtf8String)
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

  test("failing stream") {
    val store = binStore()
    (for {
      id <- ExampleData.fail.through(store.insert(hint)).attempt
      _ = assert(id.isLeft)
    } yield ()).compile.drain
  }

  test("evaluate effects once") {
    val store = binStore()
    val exampleData = new BinaryStoreSpec.ChunkWithEffects(30, 15)

    for {
      id <- exampleData.stream
        .through(store.insert(hint))
        .compile
        .lastOrError

      bin <- store.findBinary(id, ByteRange.All).getOrElse(sys.error("not found"))
      binSha <- bin.messageDigest(md).compile.to(ByteVector)
      _ = assertEquals(binSha, exampleData.sha256)
      _ = assertEquals(exampleData.getState, 15)
    } yield ()
  }
}

object BinaryStoreSpec {

  final class ChunkWithEffects(chunkSize: Int, length: Int) {
    private[this] val counter = new AtomicInteger(0)

    val stream: Stream[IO, Byte] =
      Stream
        .eval(IO {
          val n = counter.getAndIncrement()
          Chunk.vector(Vector.fill(chunkSize)(n.toByte))
        })
        .repeatN(length)
        .flatMap(Stream.chunk)

    val sha256 =
      Stream
        .range(0, length)
        .flatMap(n => Stream.chunk(Chunk.vector(Vector.fill(chunkSize)(n.toByte))))
        .through(fs2.hash.sha256)
        .to(ByteVector)

    def getState: Int = counter.get()
  }
}
