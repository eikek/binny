package binny.pglo

import scala.collection.immutable.Seq

import binny.BinaryStore
import binny.jdbc.SwapFind
import binny.spec.BinaryStoreSpec
import binny.util.Logger
import cats.effect._

class PgLoBinaryStoreStatefulTest
    extends BinaryStoreSpec[BinaryStore[IO]]
    with PgStoreFixtures {

  val logger = Logger.stdout[IO](Logger.Level.Off, getClass.getSimpleName)
  val config = PgLoConfig.default.copy(chunkSize = 210 * 1024)

  val binStoreFixture: Fixture[BinaryStore[IO]] =
    ResourceSuiteLocalFixture(
      "pglo-store",
      Resource.pure(SwapFind(makeBinStore(logger, PgLoConfig.default)))
    )

  override def munitFixtures: Seq[Fixture[_]] = List(binStoreFixture)
  override def binStore = binStoreFixture()
}
