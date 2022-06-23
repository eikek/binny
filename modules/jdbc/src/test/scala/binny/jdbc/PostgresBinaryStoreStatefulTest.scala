package binny.jdbc

import binny.BinaryStore
import binny.spec.BinaryStoreSpec
import binny.util.Logger
import cats.effect._
import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

class PostgresBinaryStoreStatefulTest
    extends BinaryStoreSpec[BinaryStore[IO]]
    with DbFixtures {

  val logger = Logger.stdout[IO](Logger.Level.Off, getClass.getSimpleName)
  val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(DockerImageName.parse("postgres:13"))

  val binStoreFixture: Fixture[BinaryStore[IO]] =
    ResourceSuiteLocalFixture(
      "pg-store",
      Resource
        .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
        .map(cnt =>
          SwapFind(
            makeBinStore(cnt, logger, JdbcStoreConfig.default, createSchema = true)
          )
        )
    )

  override def munitFixtures: Seq[Fixture[_]] = List(binStoreFixture)
  override def binStore = binStoreFixture()
}
