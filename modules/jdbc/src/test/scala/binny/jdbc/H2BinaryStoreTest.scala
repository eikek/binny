package binny.jdbc

import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

import binny.util.Logger
import cats.effect._

class H2BinaryStoreTest extends GenericJdbcStoreSpec with DbFixtures {
  private[this] val schemaCreated = new AtomicBoolean(false)

  val logger = Logger.stdout[IO](Logger.Level.Off, getClass.getSimpleName)

  val binStore1: Fixture[GenericJdbcStore[IO]] =
    ResourceSuiteLocalFixture(
      "h2-store",
      Resource.pure(
        h2BinStore(
          "h2bin",
          logger,
          JdbcStoreConfig.default,
          schemaCreated.compareAndSet(false, true)
        )
      )
    )

  val binStore2: Fixture[GenericJdbcStore[IO]] =
    ResourceSuiteLocalFixture(
      "h2-store2",
      Resource.pure(
        h2BinStore(
          "h2bin",
          logger,
          JdbcStoreConfig.default.copy(chunkSize = 200),
          schemaCreated.compareAndSet(false, true)
        )
      )
    )

  override def dataSource: DataSource =
    ConnectionConfig.h2Memory("h2bin").dataSource

  override def munitFixtures: Seq[Fixture[_]] = List(binStore1, binStore2)

  override def binStore = binStore1()
}
