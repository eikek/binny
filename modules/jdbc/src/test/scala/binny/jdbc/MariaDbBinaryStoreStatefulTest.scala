package binny.jdbc

import binny.BinaryStore
import binny.spec.BinaryStoreSpec
import binny.util.Logger
import cats.effect._
import com.dimafeng.testcontainers.MariaDBContainer
import org.testcontainers.utility.DockerImageName

class MariaDbBinaryStoreStatefulTest
    extends BinaryStoreSpec[BinaryStore[IO]]
    with DbFixtures {
  val logger = Logger.stdout[IO](Logger.Level.Off, getClass.getSimpleName)

  val containerDef: MariaDBContainer.Def =
    MariaDBContainer.Def(DockerImageName.parse("mariadb:10.5"))

  val binStoreFixture = ResourceSuiteLocalFixture(
    "jdbc-store",
    Resource
      .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
      .map(cnt =>
        SwapFind(makeBinStore(cnt, logger, JdbcStoreConfig.default, createSchema = true))
      )
  )

  override def munitFixtures: Seq[Fixture[_]] = List(binStoreFixture)

  override def binStore = binStoreFixture()
}
