package binny.jdbc

import binny.ContentTypeDetect.Hint
import binny._
import binny.util.Stopwatch
import cats.effect._
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import fs2.{Chunk, Stream}
import munit.CatsEffectSuite
import org.log4s.getLogger
import org.testcontainers.utility.DockerImageName

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
        _ <- store.assertInsertAndLoad(ExampleData.empty[IO])
        _ <- store.assertInsertAndLoad(ExampleData.helloWorld)
        _ <-
          for {
            data <- store.insertAndLoadRange(ExampleData.helloWorld, ByteRange(2, 5))
            str <- data.bytes.through(fs2.text.utf8.decode).foldMonoid.compile.lastOrError
            _ = assertEquals(str, "llo W")
          } yield ()
      } yield ()
    }
  }

  test("larger file with findStateful") {
    val data = Stream
      .emit("abcdefghijklmnopqrstuvwxyz")
      .repeat
      .take(20000)
      .flatMap(str => Stream.chunk(Chunk.array(str.getBytes)))
      .covary[IO]
      .buffer(32 * 1024)
    withContainers { cnt =>
      val store = makeBinStore(cnt, logger, cfg)

      for {
        givenSha <- data.through(fs2.hash.sha256).chunks.head.compile.lastOrError
        id <- Stopwatch.wrap(d => logger.debug(s"Insert larger file took: $d")) {
          store.insert(data, Hint.none)
        }
        w <- Stopwatch.start[IO]
        elOpt <- store.findBinaryStateful(id, ByteRange.All).value
        el = elOpt.getOrElse(sys.error("Binary not found"))
        elAttr <- el.computeAttributes(ContentTypeDetect.none, Hint.none)
        _ <- Stopwatch.show(w)(d => logger.debug(s"Loading and sha256 took: $d"))
        _ = assertEquals(elAttr.sha256, givenSha.toByteVector)
      } yield ()
    }
  }

  test("load range with findStateful") {
    withContainers { cnt =>
      val store = makeBinStore(cnt, logger, cfg)
      for {
        id <- store.insert(ExampleData.helloWorld, Hint.none)
        elOpt <- store.findBinaryStateful(id, ByteRange(2, 5)).value
        el = elOpt.getOrElse(sys.error("Binary not found"))
        str <- el.bytes.through(fs2.text.utf8.decode).foldMonoid.compile.lastOrError
        _ = assertEquals(str, "llo W")
      } yield ()
    }
  }
}
