package binny.pglo

import binny.BinaryStore
import binny.jdbc.{Docker, SwapFind}
import binny.spec.BinaryStoreSpec
import cats.effect._
import com.dimafeng.testcontainers.PostgreSQLContainer
import munit.CatsEffectSuite
import org.testcontainers.utility.DockerImageName

class PgLoBinaryStoreStatefulTest
    extends CatsEffectSuite
    with BinaryStoreSpec[BinaryStore[IO]]
    with PgStoreFixtures {

  val config = PgLoConfig.default.copy(chunkSize = 210 * 1024)

  val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(DockerImageName.parse("postgres:13"))

  override lazy val binStore: Fixture[BinaryStore[IO]] =
    ResourceSuiteLocalFixture(
      "pglo-store",
      Resource
        .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
        .map(cnt => SwapFind(makeBinStore(cnt, logger, PgLoConfig.default)))
    )

  assume(Docker.existsUnsafe, "docker not present")

  override def munitFixtures: Seq[Fixture[_]] = List(binStore)
}
