package binny.jdbc

import binny.BinaryStore
import binny.spec.BinaryStoreSpec
import binny.util.Logger
import cats.effect._

class PostgresBinaryStoreStatefulTest
    extends BinaryStoreSpec[BinaryStore[IO]]
    with DbFixtures {

  val logger = Logger.stdout[IO](Logger.Level.Off, getClass.getSimpleName)

  val binStoreFixture: Fixture[BinaryStore[IO]] =
    ResourceSuiteLocalFixture(
      "pg-store",
      Resource
        .pure(
          SwapFind(
            makeBinStore(
              ConnectionConfig.Postgres.default,
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
