package binny.jdbc

import binny.spec.{BinaryAttributeStoreSpec, BinaryStoreSpec}
import cats.effect._
import com.dimafeng.testcontainers.PostgreSQLContainer
import munit.CatsEffectSuite
import org.testcontainers.utility.DockerImageName

class PostgresBinaryStoreTest
    extends CatsEffectSuite
    with BinaryStoreSpec[JdbcBinaryStore[IO]]
    with BinaryAttributeStoreSpec[JdbcAttributeStore[IO]]
    with DbFixtures {

  val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(DockerImageName.parse("postgres:13"))

  lazy val binStore: Fixture[JdbcBinaryStore[IO]] =
    ResourceSuiteLocalFixture(
      "pg-store",
      Resource
        .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
        .map(cnt => makeBinStore(cnt, logger, JdbcStoreConfig.default))
    )

  lazy val attrStore: Fixture[JdbcAttributeStore[IO]] =
    ResourceSuiteLocalFixture(
      "pg-attr-store",
      Resource
        .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
        .map(cnt => makeAttrStore(cnt, logger, JdbcAttrConfig.default))
    )

  override def munitFixtures: Seq[Fixture[_]] = List(binStore, attrStore)
}
