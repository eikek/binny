package binny.jdbc

import binny._
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import org.testcontainers.utility.DockerImageName
import cats.effect._
import munit.CatsEffectSuite
import org.log4s.getLogger

class BasicPostgresStoreTest
    extends CatsEffectSuite
    with TestContainerForAll
    with BinaryStoreAsserts
    with DbFixtures {

  implicit private[this] val logger = Log4sLogger[IO](getLogger)
  override val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(DockerImageName.parse("postgres:13"))
  val cfg = JdbcStoreConfig.default.copy(chunkSize = 100 * 1024)

  assume(Docker.existsUnsafe, "docker not present")

  test("basic assertions") {
    withContainers { cnt =>
      val store = makeBinStore(cnt, logger, cfg)
      for {
        _ <- store.assertInsertAndLoadLargerFile()
        _ <- store.assertInsertAndDelete()
        //_ <- store.assertInsertAndLoad(ExampleData.empty[IO])
        _ <- store.assertInsertAndLoad(ExampleData.helloWorld)
//        _ <-
//          for {
//            data <- store.insertAndLoadRange(ExampleData.helloWorld, ByteRange(2, 5))
//            str <- data.bytes.through(fs2.text.utf8.decode).foldMonoid.compile.lastOrError
//            _ = assertEquals(str, "llo W")
//          } yield ()
      } yield ()
    }
  }

}
