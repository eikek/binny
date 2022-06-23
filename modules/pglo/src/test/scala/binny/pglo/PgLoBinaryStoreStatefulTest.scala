package binny.pglo

import binny.BinaryStore
import binny.jdbc.{Docker, SwapFind}
import binny.spec.BinaryStoreSpec
import binny.util.Logger
import cats.effect._
import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

class PgLoBinaryStoreStatefulTest
    extends BinaryStoreSpec[BinaryStore[IO]]
    with PgStoreFixtures {

  val logger = Logger.stdout[IO](Logger.Level.Off, getClass.getSimpleName)
  val config = PgLoConfig.default.copy(chunkSize = 210 * 1024)

  val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(DockerImageName.parse("postgres:13"))

  val binStoreFixture: Fixture[BinaryStore[IO]] =
    ResourceSuiteLocalFixture(
      "pglo-store",
      Resource
        .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
        .map(cnt => SwapFind(makeBinStore(cnt, logger, PgLoConfig.default)))
    )

  assume(Docker.existsUnsafe, "docker not present")

  override def munitFixtures: Seq[Fixture[_]] = List(binStoreFixture)
  override def binStore = binStoreFixture()
}
