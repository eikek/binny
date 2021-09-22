package binny.jdbc

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}

import binny.util.Logger
import cats.effect._
import com.dimafeng.testcontainers.{JdbcDatabaseContainer, MariaDBContainer}
import org.testcontainers.utility.DockerImageName

class MariaDbBinaryStoreTest extends GenericJdbcStoreSpec with DbFixtures {
  private[this] val schemaCreated = new AtomicBoolean(false)

  val logger = Logger.stdout[IO](Logger.Level.Off, getClass.getSimpleName)

  val container = new AtomicReference[JdbcDatabaseContainer]()

  val containerDef: MariaDBContainer.Def =
    MariaDBContainer.Def(DockerImageName.parse("mariadb:10.5"))

  val binStore = ResourceSuiteLocalFixture(
    "mariadb-store",
    Resource
      .make(IO {
        val cnt = containerDef.start()
        container.set(cnt)
        cnt
      })(cnt =>
        IO {
          cnt.stop()
          container.set(null)
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
      "mariadb-store2",
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

  override def munitFixtures: Seq[Fixture[_]] = List(binStore, binStore2)

  def dataSource =
    ConnectionConfig(
      container.get().jdbcUrl,
      container.get().username,
      container.get().password
    ).dataSource
}
