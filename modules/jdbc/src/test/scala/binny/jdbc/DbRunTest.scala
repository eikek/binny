package binny.jdbc

import javax.sql.DataSource

import binny.Log4sLogger
import binny.jdbc.impl.DbRun
import binny.jdbc.impl.Implicits._
import cats.effect._
import munit.CatsEffectSuite
import org.log4s.getLogger

class DbRunTest extends CatsEffectSuite with DbFixtures {
  implicit private[this] val logger: Logger[IO] = Log4sLogger[IO](getLogger)

  val config = JdbcStoreConfig.default
  val dataSource: FunFixture[DataSource] = h2MemoryDataSource

  dataSource.test("hasNext on empty table") { ds =>
    DatabaseSetup.runData[IO](Dbms.PostgreSQL, ds, "file_chunk").unsafeRunSync()
    val v = DbRun
      .query[IO]("SELECT * FROM file_chunk")(_ => ())
      .use(rs => DbRun.hasNext[IO](rs))
      .execute(ds)
    assertIO(v, false)
  }

  dataSource.test("hasNext on non-empty table") { ds =>
    val v =
      for {
        _ <- DatabaseSetup.runAttr[IO](Dbms.PostgreSQL, ds, "attrs")
        _ <- DbRun
          .executeUpdate[IO](
            "INSERT INTO attrs (file_id,sha256,content_type,length) VALUES ('a', 'b', 'c', 0)"
          )
          .execute(ds)
        v <- DbRun
          .query[IO]("SELECT * FROM attrs")(_ => ())
          .use(rs => DbRun.hasNext[IO](rs))
          .execute(ds)
      } yield v
    assertIO(v, true)
  }

  dataSource.test("tx rollback") { ds =>
    val txBody = for {
      _ <- DbRun
        .executeUpdate[IO](
          "INSERT INTO attrs (file_id,sha256,content_type,length) VALUES ('a', 'b', 'c', 0)"
        )
      _ <- DbRun.fail[IO, Unit](new Exception("Oops!"))
    } yield ()

    val v =
      for {
        _ <- DatabaseSetup.runAttr[IO](Dbms.PostgreSQL, ds, "attrs")
        _ <- txBody.inTX.attempt.execute(ds)
        v <- DbRun
          .query[IO]("SELECT * FROM attrs")(_ => ())
          .use(rs => DbRun.hasNext[IO](rs))
          .execute(ds)
      } yield v
    assertIO(v, false)
  }
}
