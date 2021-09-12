package binny.jdbc

import javax.sql.DataSource

import binny.Log4sLogger
import binny.jdbc.impl.DbRun
import binny.jdbc.impl.Implicits._
import cats.effect._
import munit.CatsEffectSuite
import org.log4s.getLogger

class DbRunTest extends CatsEffectSuite with DbFixtures {
  implicit private[this] val logger = Log4sLogger[IO](getLogger)

  val config                             = JdbcStoreConfig.default
  val dataSource: FunFixture[DataSource] = h2MemoryDataSource

  dataSource.test("exists query") { ds =>
    DatabaseSetup.runData[IO](Dbms.PostgreSQL, ds, "file_chunk").unsafeRunSync()
    DbRun
      .update[IO]("UPDATE file_chunk set file_id = ? where file_id = ?") { ps =>
        ps.setString(1, "abc")
        ps.setString(2, "def")
      }
      .execute(ds)
      .unsafeRunSync()

//    val v = DbRun.exists(config.dataTable, BinaryId("test")).execute[IO](ds)
//    assertIO(v, false)
  }
}
