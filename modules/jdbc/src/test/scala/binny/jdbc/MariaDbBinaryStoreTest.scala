package binny.jdbc

import binny.Log4sLogger
import binny.spec.BinaryStoreSpec
import cats.effect._
import com.dimafeng.testcontainers.MariaDBContainer
import org.testcontainers.utility.DockerImageName

class MariaDbBinaryStoreTest extends BinaryStoreSpec[JdbcBinaryStore[IO]] with DbFixtures {
  private [this] val logger = Log4sLogger[IO](org.log4s.getLogger)

  val containerDef: MariaDBContainer.Def =
    MariaDBContainer.Def(DockerImageName.parse("mariadb:10.5"))

  lazy val binStore: SyncIO[FunFixture[JdbcBinaryStore[IO]]] =
    ResourceFixture(
      Resource
        .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
        .map(cnt => makeBinStore(cnt, logger, JdbcStoreConfig.default))
    )
}
