package binny.jdbc

import javax.sql.DataSource

import binny.jdbc.impl.DbRun
import binny.jdbc.impl.Implicits._
import binny.util.Logger
import cats.effect._
import munit.CatsEffectSuite

class DbRunTest extends CatsEffectSuite with DbFixtures {
  implicit private[this] val logger: Logger[IO] =
    Logger.stdout[IO](Logger.Level.Off, getClass.getSimpleName)

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
        _ <- DatabaseSetup.runData[IO](Dbms.PostgreSQL, ds, "file_chunk")
        _ <- DbRun
          .executeUpdate[IO](
            "INSERT INTO file_chunk (file_id,chunk_nr,chunk_len,chunk_data) VALUES ('a', 0, 1, 'x')"
          )
          .execute(ds)
        v <- DbRun
          .query[IO]("SELECT * FROM file_chunk")(_ => ())
          .use(rs => DbRun.hasNext[IO](rs))
          .execute(ds)
      } yield v
    assertIO(v, true)
  }

  dataSource.test("tx rollback") { ds =>
    val txBody = for {
      _ <- DbRun
        .executeUpdate[IO](
          "INSERT INTO file_chunk (file_id,chunk_nr,chunk_len,chunk_data) VALUES ('a', 0, 1, 'x')"
        )
      _ <- DbRun.fail[IO, Unit](new Exception("Oops!"))
    } yield ()

    val v =
      for {
        _ <- DatabaseSetup.runData[IO](Dbms.PostgreSQL, ds, "file_chunk")
        _ <- txBody.inTX.attempt.execute(ds)
        v <- DbRun
          .query[IO]("SELECT * FROM file_chunk")(_ => ())
          .use(rs => DbRun.hasNext[IO](rs))
          .execute(ds)
      } yield v
    assertIO(v, false)
  }
}
