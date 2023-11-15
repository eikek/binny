package binny.pglo

import scala.collection.immutable.Seq

import binny.spec.BinaryStoreSpec
import binny.util.Logger
import cats.effect._

class PgLoBinaryStoreTest
    extends BinaryStoreSpec[PgLoBinaryStore[IO]]
    with PgStoreFixtures {
  val logger = Logger.stdout[IO](Logger.Level.Off, getClass.getSimpleName)
  val config = PgLoConfig.default.copy(chunkSize = 210 * 1024)

  val binStoreFixture: Fixture[PgLoBinaryStore[IO]] =
    ResourceSuiteLocalFixture(
      "pglo-store",
      Resource.pure(makeBinStore(logger, PgLoConfig.default))
    )

  override def munitFixtures: Seq[Fixture[_]] = List(binStoreFixture)
  override def binStore = binStoreFixture()
}
