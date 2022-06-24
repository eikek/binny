package binny.jdbc

import java.util.concurrent.atomic.AtomicBoolean

import binny.util.Logger
import cats.effect._

class MariaDbBinaryStoreTest extends GenericJdbcStoreSpec with DbFixtures {
  private[this] val schemaCreated = new AtomicBoolean(false)

  val logger = Logger.stdout[IO](Logger.Level.Off, getClass.getSimpleName)

  val binStore1 = ResourceSuiteLocalFixture(
    "mariadb-store",
    Resource
      .pure(
        makeBinStore(
          ConnectionConfig.MariaDB.default,
          logger,
          JdbcStoreConfig.default,
          schemaCreated.compareAndSet(false, true)
        )
      )
  )

  val binStore2: Fixture[GenericJdbcStore[IO]] =
    ResourceSuiteLocalFixture(
      "mariadb-store2",
      Resource
        .pure(
          makeBinStore(
            ConnectionConfig.MariaDB.default,
            logger,
            JdbcStoreConfig.default.withChunkSize(200),
            schemaCreated.compareAndSet(false, true)
          )
        )
    )

  override def munitFixtures: Seq[Fixture[_]] = List(binStore1, binStore2)

  override def binStore = binStore1()

  def dataSource =
    ConnectionConfig.MariaDB.default.dataSource
}
