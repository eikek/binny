package binny.pglo

import binny._
import binny.jdbc.Docker
import binny.spec.BinaryStoreSpec
import cats.effect._
import com.dimafeng.testcontainers.PostgreSQLContainer
import org.log4s.getLogger
import org.testcontainers.utility.DockerImageName

class PgLoBinaryStoreTest
    extends BinaryStoreSpec[PgLoBinaryStore[IO]]
    with PgStoreFixtures {

  implicit private[this] val logger = Log4sLogger[IO](getLogger)

  val config = PgLoConfig.default.copy(chunkSize = 210 * 1024)

  val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(DockerImageName.parse("postgres:13"))

  override lazy val binStore: SyncIO[FunFixture[PgLoBinaryStore[IO]]] =
    ResourceFixture(
      Resource
        .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
        .map(cnt => makeBinStore(cnt, logger, PgLoConfig.default))
    )

  assume(Docker.existsUnsafe, "docker not present")

}
