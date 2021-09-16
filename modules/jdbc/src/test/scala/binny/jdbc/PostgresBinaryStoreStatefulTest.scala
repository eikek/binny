package binny.jdbc

import binny.BinaryStore
import binny.spec.BinaryStoreSpec
import cats.effect._
import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

class PostgresBinaryStoreStatefulTest
    extends BinaryStoreSpec[BinaryStore[IO]]
    with DbFixtures {

  val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(DockerImageName.parse("postgres:13"))

  val binStore: Fixture[BinaryStore[IO]] =
    ResourceSuiteLocalFixture(
      "pg-store",
      Resource
        .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
        .map(cnt => SwapFind(makeBinStore(cnt, logger, JdbcStoreConfig.default)))
    )

  override def munitFixtures: Seq[Fixture[_]] = List(binStore)
}
