package binny.jdbc

import binny.Log4sLogger
import binny.spec.BinaryStoreSpec
import cats.effect._
import com.dimafeng.testcontainers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

class PostgresBinaryStoreTest
    extends BinaryStoreSpec[JdbcBinaryStore[IO]]
    with DbFixtures {
  private[this] val logger = Log4sLogger[IO](org.log4s.getLogger)

  val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(DockerImageName.parse("postgres:13"))

  lazy val binStore: SyncIO[FunFixture[JdbcBinaryStore[IO]]] =
    ResourceFixture(
      Resource
        .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
        .map(cnt => makeBinStore(cnt, logger, JdbcStoreConfig.default))
    )
}
