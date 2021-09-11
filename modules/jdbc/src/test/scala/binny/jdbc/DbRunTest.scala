package binny.jdbc

import binny.BinaryId
import binny.jdbc.impl.DbRun
import cats.effect._
import munit.CatsEffectSuite
import DbRun._
import org.log4s.getLogger

import javax.sql.DataSource

class DbRunTest extends CatsEffectSuite {

  val config = JdbcStoreConfig.default
  val dataSource: FunFixture[DataSource] = FunFixture(
    setup = test => {
      val ds = ConnectionConfig.h2Memory(test.name.replaceAll("\\s+", "_")).dataSource
      getLogger.debug(s"Creating database tables for $config")
      DatabaseSetup.postgres[IO](ds, config).unsafeRunSync()
      ds
    },
    teardown = _ => ()
  )

  dataSource.test("exists query") { ds =>
    val v = DbRun.exists(config.dataTable, BinaryId("test")).execute[IO](ds)
    assertIO(v, false)
  }
}
