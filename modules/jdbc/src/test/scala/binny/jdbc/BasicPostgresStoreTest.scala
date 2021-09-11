package binny.jdbc

import binny.{BinaryStoreAsserts, ExampleData}
import com.dimafeng.testcontainers.PostgreSQLContainer
import com.dimafeng.testcontainers.munit.TestContainerForAll
import org.testcontainers.utility.DockerImageName
import cats.effect._
import munit.CatsEffectSuite

class BasicPostgresStoreTest
    extends CatsEffectSuite
    with TestContainerForAll
    with BinaryStoreAsserts {

  override val containerDef: PostgreSQLContainer.Def =
    PostgreSQLContainer.Def(DockerImageName.parse("postgres:13"))
  val cfg = JdbcStoreConfig.default.copy(chunkSize = 1024)

  assume(Docker.existsUnsafe, "docker not present")

  def makeStore(cnt: Containers): JdbcBinaryStore[IO] = {
    val cc    = ConnectionConfig(cnt.jdbcUrl, cnt.username, cnt.password)
    val store = JdbcBinaryStore[IO](cc.dataSource, cfg)
    DatabaseSetup.postgres[IO](cc.dataSource, cfg).unsafeRunSync()
    store
  }

  test("basic assertions") {
    withContainers { cnt =>
      val store = makeStore(cnt)
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
