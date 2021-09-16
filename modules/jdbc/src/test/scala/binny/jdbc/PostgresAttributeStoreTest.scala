package binny.jdbc

import binny.spec.BinaryAttributeStoreSpec
import cats.effect._
import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

class PostgresAttributeStoreTest
    extends BinaryAttributeStoreSpec[JdbcAttributeStore[IO]]
    with DbFixtures {

  val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(DockerImageName.parse("postgres:13"))

  val attrStore: Fixture[JdbcAttributeStore[IO]] =
    ResourceSuiteLocalFixture(
      "pg-attr-store",
      Resource
        .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
        .map(cnt => makeAttrStore(cnt, logger, JdbcAttrConfig.default))
    )

  override def munitFixtures: Seq[Fixture[_]] = List(attrStore)
}
