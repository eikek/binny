package binny.pglo

import binny._
import binny.jdbc.Docker
import binny.util.Stopwatch
import cats.effect._
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import munit.CatsEffectSuite
import org.log4s.getLogger
import org.testcontainers.utility.DockerImageName

class PgLoBinaryStoreTest
    extends CatsEffectSuite
    with BinaryStoreAsserts
    with TestContainerForAll
    with PgStoreFixtures {

  implicit private[this] val logger = Log4sLogger[IO](getLogger)

  val config = PgLoConfig.default.copy(chunkSize = 210 * 1024)

  override val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(DockerImageName.parse("postgres:13"))

  assume(Docker.existsUnsafe, "docker not present")

  test("insert and load") {
    withContainers { cnt =>
      val store = makeBinStore(cnt, logger, config)

      for {
        w <- Stopwatch.start[IO]
        _ <- Stopwatch.wrap(d => logger.info(s"Test1: $d")) {
          store.assertInsertAndLoad(ExampleData.helloWorld)
        }
        _ <- Stopwatch.wrap(d => logger.info(s"Test2: $d")) {
          store.assertInsertAndLoad(ExampleData.empty[IO])
        }
        _ <- Stopwatch.wrap(d => logger.info(s"Test3: $d")) {
          store.assertInsertAndLoadLargerFile()
        }
        _ <- Stopwatch.show(w)(d => logger.info(s"Tests took: $d"))
      } yield ()
    }
  }

  test("insert and load range") {
    withContainers { cnt =>
      val store = makeBinStore(cnt, logger, config)

      for {
        data <- store.insertAndLoadRange(ExampleData.helloWorld, ByteRange(2, 5))
        str  <- data.bytes.through(fs2.text.utf8.decode).foldMonoid.compile.lastOrError
        _ = assertEquals(str, "llo W")
      } yield ()
    }
  }

  test("insert and delete") {
    withContainers { cnt =>
      val store = makeBinStore(cnt, logger, config)
      store.assertInsertAndDelete()
    }
  }
}
