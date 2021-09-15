package binny.jdbc

import binny.spec.{BinaryAttributeStoreSpec, BinaryStoreSpec}
import cats.effect._
import com.dimafeng.testcontainers.MariaDBContainer
import munit.CatsEffectSuite
import org.testcontainers.utility.DockerImageName

class MariaDbBinaryStoreTest
    extends CatsEffectSuite
    with BinaryStoreSpec[JdbcBinaryStore[IO]]
    with BinaryAttributeStoreSpec[JdbcAttributeStore[IO]]
    with DbFixtures {

  val containerDef: MariaDBContainer.Def =
    MariaDBContainer.Def(DockerImageName.parse("mariadb:10.5"))

  lazy val binStore = ResourceSuiteLocalFixture(
    "jdbc-store",
    Resource
      .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
      .map(cnt => makeBinStore(cnt, logger, JdbcStoreConfig.default))
  )

  lazy val attrStore = ResourceSuiteLocalFixture(
    "attr-store",
    Resource
      .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
      .map(cnt => makeAttrStore(cnt, logger, JdbcAttrConfig.default))
  )

  override def munitFixtures: Seq[Fixture[_]] = List(binStore, attrStore)

}
