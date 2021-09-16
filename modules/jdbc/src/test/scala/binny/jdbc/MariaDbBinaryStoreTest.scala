package binny.jdbc

import binny.spec.BinaryStoreSpec
import cats.effect._
import com.dimafeng.testcontainers.MariaDBContainer
import org.testcontainers.utility.DockerImageName

class MariaDbBinaryStoreTest
    extends BinaryStoreSpec[JdbcBinaryStore[IO]]
    with DbFixtures {

  val containerDef: MariaDBContainer.Def =
    MariaDBContainer.Def(DockerImageName.parse("mariadb:10.5"))

  val binStore = ResourceSuiteLocalFixture(
    "jdbc-store",
    Resource
      .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
      .map(cnt => makeBinStore(cnt, logger, JdbcStoreConfig.default))
  )

  override def munitFixtures: Seq[Fixture[_]] = List(binStore)
}
