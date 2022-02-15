package binny.stacked

import binny._
import binny.util.Logger
import cats.effect.IO
import munit.CatsEffectSuite

class StackedBinaryStoreTest extends CatsEffectSuite with StreamAssertion {
  private val log = Logger.stdout[IO]()

  test("Read only from the main store, insert into all") {
    for {
      bs1 <- MemoryBinaryStore.createEmpty[IO]
      bs2 <- MemoryBinaryStore.createEmpty[IO]
      stack = StackedBinaryStore.of(log, bs1, bs2, SimpleBinaryStoreFixtures.throwOnRead)
      id <- ExampleData.logoPng.through(stack.insert(Hint.none)).compile.lastOrError
      b0 <- stack.findBinary(id, ByteRange.All).value
      b1 <- bs1.findBinary(id, ByteRange.All).value
      b2 <- bs2.findBinary(id, ByteRange.All).value
      _ <- assertExistAndEquals(b0, b1)
      _ <- assertExistAndEquals(b1, b2)
    } yield ()
  }

  test("delete from all stores") {
    for {
      bs1 <- MemoryBinaryStore.createEmpty[IO]
      bs2 <- MemoryBinaryStore.createEmpty[IO]
      stack = StackedBinaryStore.of(log, bs1, bs2)
      id <- ExampleData.logoPng.through(stack.insert(Hint.none)).compile.lastOrError
      _ <- stack.delete(id)
      b1 <- bs1.findBinary(id, ByteRange.All).value
      b2 <- bs2.findBinary(id, ByteRange.All).value
      _ = assertEquals(b1, None)
      _ = assertEquals(b2, None)
      _ <- assertIO(bs1.exists(id), false)
      _ <- assertIO(bs2.exists(id), false)
    } yield ()
  }

  test("when main fails, don't insert into other stores") {
    for {
      bs1 <- MemoryBinaryStore.createEmpty[IO]
      stack = StackedBinaryStore.of(log, SimpleBinaryStoreFixtures.throwAlways, bs1)
      noId <- ExampleData.logoPng
        .through(stack.insert(Hint.none))
        .compile
        .lastOrError
        .attempt
      _ = assert(noId.isLeft)
      b1 <- bs1.listIds(None, 10).compile.toVector
      _ = assertEquals(b1, Vector.empty)
    } yield ()
  }

  test("when main fails, don't delete other stores") {
    for {
      bs1 <- MemoryBinaryStore.createEmpty[IO]
      stack = StackedBinaryStore.of(log, SimpleBinaryStoreFixtures.throwAlways, bs1)
      id <- ExampleData.logoPng
        .through(bs1.insert(Hint.none))
        .compile
        .lastOrError

      del <- stack.delete(id).attempt
      _ = assert(del.isLeft)

      _ <- assertIO(bs1.exists(id), true)
    } yield ()
  }
}
