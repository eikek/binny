package binny

import binny.spec.BinaryStoreSpec
import cats.effect._

class MemoryBinaryStoreTest extends BinaryStoreSpec[MemoryBinaryStore[IO]] {
  val binStoreFixture = ResourceSuiteLocalFixture(
    "fs-store",
    Resource.eval[IO, MemoryBinaryStore[IO]](MemoryBinaryStore.create[IO](Map.empty))
  )

  override def munitFixtures: Seq[Fixture[_]] = List(binStoreFixture)

  override def binStore: MemoryBinaryStore[IO] = binStoreFixture()
}
