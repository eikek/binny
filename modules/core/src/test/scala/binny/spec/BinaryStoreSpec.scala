package binny.spec

import java.util.concurrent.atomic.AtomicInteger

import binny.ExampleData._
import binny._
import binny.util.{Logger, Stopwatch}
import cats.effect._
import cats.syntax.all._
import fs2.{Chunk, Stream}
import munit.CatsEffectSuite
import scodec.bits.ByteVector

trait BinaryStoreSpec[S <: BinaryStore[IO]] extends CatsEffectSuite with StreamAssertion {
  private[this] val logger = Logger.stdout[IO](Logger.Level.Warn, getClass.getSimpleName)

  def binStore: S

  val hintTxt: Hint = Hint.filename("file2M.txt")
  def noFileError: Binary[IO] = sys.error("No binary found")

  test("insert and load concurrently") {
    val store = binStore
    for {
      ids <- Stream(ExampleData.file2M, ExampleData.helloWorld, ExampleData.logoPng)
        .covary[IO]
        .parEvalMap(3)(data => data.through(store.insert).compile.lastOrError)
        .compile
        .toVector

      files <- ids.toList.traverse(store.findBinary(_, ByteRange.All).value)
      _ = assertEquals(files.flatten.size, 3)
    } yield ()
  }

  test("insert and load") {
    val store = binStore
    Stream(ExampleData.helloWorld, Binary.empty, ExampleData.file2M.take(2203))
      .flatMap(data =>
        for {
          id <- data.through(store.insert)
          bin <- Stream.eval(
            store
              .findBinary(id, ByteRange.All)
              .getOrElse(noFileError)
          )
          _ <- Stream.eval(assertBinaryEquals(bin, data))
        } yield ()
      )
      .compile
      .drain
  }

  test("load non existing id") {
    val store = binStore
    for {
      id <- BinaryId.random[IO]
      file <- store.findBinary(id, ByteRange.All).value
      _ = assertEquals(file, None)
    } yield ()
  }

  test("file exists") {
    val store = binStore
    for {
      id0 <- IO(BinaryId("a1"))
      id1 <- ExampleData.helloWorld[IO].through(store.insert).compile.lastOrError
      ex0 <- store.exists(id0)
      ex1 <- store.exists(id1)
      _ = {
        assert(!ex0)
        assert(ex1)
      }
    } yield ()
  }

  test("insert and load large file") {
    val store = binStore
    (for {
      w <- Stream.eval(Stopwatch.start[IO])
      id <- ExampleData.file2M.through(store.insert)
      bin <- Stream.eval(store.findBinary(id, ByteRange.All).getOrElse(noFileError))
      attr <- bin.computeAttributes(ContentTypeDetect.probeFileType, hintTxt)
      _ = assertEquals(attr, ExampleData.file2MAttr)
      _ <- Stream.eval(Stopwatch.show(w)(d => logger.debug(s"Large file test took: $d")))
    } yield ()).compile.drain
  }

  test("load small range") {
    val store = binStore
    (for {
      id <- ExampleData.helloWorld[IO].through(store.insert)
      bin <- Stream.eval(store.findBinary(id, ByteRange(2, 5)).getOrElse(noFileError))
      str <- Stream.eval(bin.readUtf8String)
      _ = assertEquals(str, "llo W")
    } yield ()).compile.drain
  }

  test("delete") {
    val store = binStore
    (for {
      id <- ExampleData.helloWorld[IO].through(store.insert)
      _ <- Stream.eval(store.findBinary(id, ByteRange.All).getOrElse(noFileError))
      _ <- Stream.eval(store.delete(id))
      res <- Stream.eval(store.findBinary(id, ByteRange.All).value)
      _ = assertEquals(res, None)
    } yield ()).compile.drain
  }

  test("failing stream") {
    val store = binStore
    (for {
      id <- ExampleData.fail.through(store.insert).attempt
      _ = assert(id.isLeft)
    } yield ()).compile.drain
  }

  test("evaluate effects once") {
    val store = binStore
    val exampleData = new BinaryStoreSpec.ChunkWithEffects(30, 15)

    for {
      id <- exampleData.stream
        .through(store.insert)
        .compile
        .lastOrError

      bin <- store.findBinary(id, ByteRange.All).getOrElse(sys.error("not found"))
      binSha <- shaString(bin)
      _ = assertEquals(binSha, exampleData.sha256.toHex)
      _ = assertEquals(exampleData.getState, 15)
    } yield ()
  }

  test("listing binary ids") {
    val store = binStore

    val id1 = BinaryId("abc123")
    val id2 = BinaryId("abc678")
    val id3 = BinaryId("oo00oo")
    Stream(
      id1 -> ExampleData.logoPng,
      id2 -> ExampleData.file2M,
      id3 -> ExampleData.helloWorld[IO]
    )
      .flatMap { case (id, file) => file.through(store.insertWith(id)) }
      .compile
      .drain
      .unsafeRunSync()

    val all1 = store.listIds(Some(""), 10).compile.toVector.unsafeRunSync()
    val all2 = store.listIds(None, 10).compile.toVector.unsafeRunSync()
    val all3 = store.listIds(None, 1).compile.toVector.unsafeRunSync()
    assertEquals(all1, all2)
    assertEquals(all1, all3)

    val abcOnly = store.listIds(Some("abc"), 50).compile.toVector.unsafeRunSync()
    assertEquals(abcOnly.toSet, Set(id1, id2))
  }

  test("computeAttrs") {
    val store = binStore
    for {
      id <- ExampleData.file2M.through(store.insert).compile.lastOrError
      attrAll <- Clock[IO].timed(
        store.computeAttr(id, hintTxt).run(AttributeName.all).value
      )
      attrNoSha <- store.computeAttr(id, hintTxt).run(AttributeName.excludeSha256).value
      attrCt <- store.computeAttr(id, hintTxt).run(AttributeName.contentTypeOnly).value
      _ <- IO.println(s"Attr took: ${Stopwatch.humanTime(attrAll._1)}")
      _ = {
        assertEquals(
          attrCt.get.contentType,
          ExampleData.file2MAttr.contentType
        )
        assertEquals(
          attrNoSha.get.copy(sha256 = ExampleData.file2MAttr.sha256),
          ExampleData.file2MAttr
        )
        assertEquals(attrAll._2.get, ExampleData.file2MAttr)
      }
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
