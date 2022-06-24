package binny.jdbc

import binny.BinaryStore
import binny.spec.BinaryStoreSpec
import binny.util.Logger
import cats.effect._

class H2BinaryStoreStatefulTest extends BinaryStoreSpec[BinaryStore[IO]] with DbFixtures {
  val logger = Logger.stdout[IO](Logger.Level.Off, getClass.getSimpleName)

  val binStoreFixture: Fixture[BinaryStore[IO]] =
    ResourceSuiteLocalFixture(
      "h2-store",
      Resource.pure(
        SwapFind(
          h2BinStore(
            "h2bin2",
            logger,
            JdbcStoreConfig.default,
            true
          )
        )
      )
    )

  override def munitFixtures: Seq[Fixture[_]] = List(binStoreFixture)

  override def binStore = binStoreFixture()
}
