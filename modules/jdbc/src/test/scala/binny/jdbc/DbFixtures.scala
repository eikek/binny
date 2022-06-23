package binny.jdbc

import javax.sql.DataSource

import binny.util.Logger
import cats.effect.IO
import com.dimafeng.testcontainers.JdbcDatabaseContainer
import munit.CatsEffectSuite

trait DbFixtures { self: CatsEffectSuite =>

  def h2MemoryDataSource: FunFixture[DataSource] = FunFixture(
    setup =
      test => ConnectionConfig.h2Memory(test.name.replaceAll("\\s+", "_")).dataSource,
    teardown = _ => ()
  )

  def h2BinStore(
      db: String,
      logger: Logger[IO],
      config: JdbcStoreConfig,
      createSchema: Boolean
  ): GenericJdbcStore[IO] = {
    implicit val log: Logger[IO] = logger
    val ds = ConnectionConfig.h2Memory(db.replaceAll("\\s+", "_")).dataSource
    val store = GenericJdbcStore[IO](ds, logger, config)
    if (createSchema) {
      DatabaseSetup
        .runData[IO](Dbms.PostgreSQL, ds, config.dataTable)
        .unsafeRunSync()
    }
    store
  }

  def makeBinStore(
      cnt: JdbcDatabaseContainer,
      logger: Logger[IO],
      cfg: JdbcStoreConfig,
      createSchema: Boolean
  ): GenericJdbcStore[IO] = {
    implicit val log: Logger[IO] = logger

    val cc = ConnectionConfig(cnt.jdbcUrl, cnt.username, cnt.password)
    val store = GenericJdbcStore[IO](cc.dataSource, logger, cfg)
    if (createSchema) {
      DatabaseSetup
        .runData[IO](
          Dbms.unsafeFromJdbcUrl(cnt.jdbcUrl),
          cc.dataSource,
          cfg.dataTable
        )
        .unsafeRunSync()
    }
    store
  }
}
