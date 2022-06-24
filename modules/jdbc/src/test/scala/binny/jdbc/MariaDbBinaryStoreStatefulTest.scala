package binny.jdbc

import binny.BinaryStore
import binny.spec.BinaryStoreSpec
import binny.util.Logger
import cats.effect._

class MariaDbBinaryStoreStatefulTest
    extends BinaryStoreSpec[BinaryStore[IO]]
    with DbFixtures {
  val logger = Logger.stdout[IO](Logger.Level.Off, getClass.getSimpleName)

  val binStoreFixture = ResourceSuiteLocalFixture(
    "jdbc-store",
    Resource
      .pure(
        SwapFind(
          makeBinStore(
            ConnectionConfig.MariaDB.default,
            logger,
            JdbcStoreConfig.default,
            createSchema = true
          )
        )
      )
  )

  override def munitFixtures: Seq[Fixture[_]] = List(binStoreFixture)

  override def binStore = binStoreFixture()
}
