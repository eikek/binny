package binny.pglo

import binny.ContentTypeDetect.Hint
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

  test("whats going on") {
    withContainers { cnt =>
      val store = makeBinStore(cnt, logger, config)

      val data = fs2.Stream
        .emit("abcdefghijklmnopqrstuvwxyz")
        .repeat
        .take(20000)
        .through(fs2.text.utf8.encode)
        .covary[IO]
        .buffer(32 * 1024)

      for {
        id <- store.insert(data, Hint.none)
        elOpt <- store.findBinary(id, ByteRange.All).value
        el = elOpt.getOrElse(sys.error("Binary not found"))

        n <- el.bytes.chunks.map(_.size).compile.foldMonoid
        k <- data.chunks.map(_.size).compile.foldMonoid
        _ <- logger.info(s">>>> Len: $n vs. $k")
        _ = assertEquals(n, k)
      } yield ()
    }
  }

  test("insert and load") {
    withContainers { cnt =>
      val store = makeBinStore(cnt, logger, config)

      for {
        _ <- Stopwatch.wrap(d => logger.info(s"Test1: $d")) {
          store.assertInsertAndLoad(ExampleData.helloWorld)
        }
      } yield ()
    }
  }

  test("insert and load empty") {
    withContainers { cnt =>
      val store = makeBinStore(cnt, logger, config)

      for {
        _ <- Stopwatch.wrap(d => logger.info(s"Test2: $d")) {
          store.assertInsertAndLoad(ExampleData.empty[IO])
        }
      } yield ()
    }
  }

  test("insert and load larger file") {
    withContainers { cnt =>
      val store = makeBinStore(cnt, logger, config)

      for {
        _ <- Stopwatch.wrap(d => logger.info(s"Test3: $d")) {
          store.assertInsertAndLoadLargerFile()
        }
      } yield ()
    }
  }

  test("insert and load range") {
    withContainers { cnt =>
      val store = makeBinStore(cnt, logger, config)

      for {
        data <- store.insertAndLoadRange(ExampleData.helloWorld, ByteRange(2, 5))
        str <- data.bytes.through(fs2.text.utf8.decode).foldMonoid.compile.lastOrError
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
