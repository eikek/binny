package binny.jdbc

import binny.spec.BinaryAttributeStoreSpec
import binny.util.Logger
import cats.effect._
import com.dimafeng.testcontainers.MariaDBContainer
import org.testcontainers.utility.DockerImageName

class MariaDbAttributeStoreTest
    extends BinaryAttributeStoreSpec[JdbcAttributeStore[IO]]
    with DbFixtures {
  val logger = Logger.stdout[IO](Logger.Level.Warn, getClass.getSimpleName)

  val containerDef: MariaDBContainer.Def =
    MariaDBContainer.Def(DockerImageName.parse("mariadb:10.5"))

  val attrStore = ResourceSuiteLocalFixture(
    "attr-store",
    Resource
      .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
      .map(cnt => makeAttrStore(cnt, logger, JdbcAttrConfig.default))
  )

  override def munitFixtures: Seq[Fixture[_]] = List(attrStore)
}
