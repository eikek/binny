package binny.jdbc

import binny.BinaryStore
import binny.spec.BinaryStoreSpec
import cats.effect._
import com.dimafeng.testcontainers.MariaDBContainer
import munit.CatsEffectSuite
import org.testcontainers.utility.DockerImageName

class MariaDbBinaryStoreStatefulTest
    extends CatsEffectSuite
    with BinaryStoreSpec[BinaryStore[IO]]
    with DbFixtures {

  val containerDef: MariaDBContainer.Def =
    MariaDBContainer.Def(DockerImageName.parse("mariadb:10.5"))

  lazy val binStore = ResourceSuiteLocalFixture(
    "jdbc-store",
    Resource
      .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
      .map(cnt => SwapFind(makeBinStore(cnt, logger, JdbcStoreConfig.default)))
  )

  override def munitFixtures: Seq[Fixture[_]] = List(binStore)

}
