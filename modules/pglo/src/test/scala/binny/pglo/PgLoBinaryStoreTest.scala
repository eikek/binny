package binny.pglo

import binny.jdbc.Docker
import binny.spec.BinaryStoreSpec
import cats.effect._
import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

class PgLoBinaryStoreTest
    extends BinaryStoreSpec[PgLoBinaryStore[IO]]
    with PgStoreFixtures {

  val config = PgLoConfig.default.copy(chunkSize = 210 * 1024)

  val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(DockerImageName.parse("postgres:13"))

  val binStore: Fixture[PgLoBinaryStore[IO]] =
    ResourceSuiteLocalFixture(
      "pglo-store",
      Resource
        .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
        .map(cnt => makeBinStore(cnt, logger, PgLoConfig.default))
    )

  assume(Docker.existsUnsafe, "docker not present")

  override def munitFixtures: Seq[Fixture[_]] = List(binStore)
}
