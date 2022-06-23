package binny.jdbc

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import binny.util.Logger
import cats.effect._
import com.dimafeng.testcontainers.{JdbcDatabaseContainer, PostgreSQLContainer}
import org.testcontainers.utility.DockerImageName

class PostgresBinaryStoreTest extends GenericJdbcStoreSpec with DbFixtures {
  private[this] val schemaCreated = new AtomicBoolean(false)

  val logger = Logger.stdout[IO](Logger.Level.Off, getClass.getSimpleName)

  val container = new AtomicReference[JdbcDatabaseContainer]()

  val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(DockerImageName.parse("postgres:13"))

  val binStore1: Fixture[GenericJdbcStore[IO]] =
    ResourceSuiteLocalFixture(
      "pg-store",
      Resource
        .make(IO {
          val cnt = containerDef.start()
          container.set(cnt)
          cnt
        })(cnt =>
          IO {
            container.set(null)
            cnt.stop()
          }
        )
        .map(cnt =>
          makeBinStore(
            cnt,
            logger,
            JdbcStoreConfig.default,
            schemaCreated.compareAndSet(false, true)
          )
        )
    )

  val binStore2: Fixture[GenericJdbcStore[IO]] =
    ResourceSuiteLocalFixture(
      "pg-store2",
      Resource
        .make(IO(container.get()))(_ => IO(()))
        .map(cnt =>
          makeBinStore(
            cnt,
            logger,
            JdbcStoreConfig.default.withChunkSize(200),
            schemaCreated.compareAndSet(false, true)
          )
        )
    )

  override def munitFixtures: Seq[Fixture[_]] = List(binStore1, binStore2)
  override def binStore = binStore1()

  def dataSource =
    ConnectionConfig(
      container.get().jdbcUrl,
      container.get().username,
      container.get().password
    ).dataSource
}
