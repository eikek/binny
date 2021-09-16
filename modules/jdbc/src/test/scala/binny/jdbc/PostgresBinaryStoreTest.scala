package binny.jdbc

import binny.spec.BinaryStoreSpec
import cats.effect._
import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

class PostgresBinaryStoreTest
    extends BinaryStoreSpec[JdbcBinaryStore[IO]]
    with DbFixtures {

  val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(DockerImageName.parse("postgres:13"))

  val binStore: Fixture[JdbcBinaryStore[IO]] =
    ResourceSuiteLocalFixture(
      "pg-store",
      Resource
        .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
        .map(cnt => makeBinStore(cnt, logger, JdbcStoreConfig.default))
    )

  override def munitFixtures: Seq[Fixture[_]] = List(binStore)
}
