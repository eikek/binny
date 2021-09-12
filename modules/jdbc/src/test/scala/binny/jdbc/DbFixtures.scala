package binny.jdbc

import javax.sql.DataSource

import binny.util.Logger
import cats.effect.IO
import com.dimafeng.testcontainers.JdbcDatabaseContainer
import munit.CatsEffectSuite

trait DbFixtures { self: CatsEffectSuite =>

  def h2MemoryDataSource: FunFixture[DataSource] = FunFixture(
    setup = test => {
      ConnectionConfig.h2Memory(test.name.replaceAll("\\s+", "_")).dataSource
    },
    teardown = _ => ()
  )

  def h2BinStore(
      logger: Logger[IO],
      config: JdbcStoreConfig
  ): FunFixture[JdbcBinaryStore[IO]] = FunFixture(
    setup = { test =>
      implicit val log = logger
      val ds           = ConnectionConfig.h2Memory(test.name.replaceAll("\\s+", "_")).dataSource
      val attrStore    = JdbcAttributeStore(JdbcAttrConfig.default, ds, logger)
      val store        = JdbcBinaryStore[IO](ds, logger, config, attrStore)
      DatabaseSetup
        .runBoth[IO](Dbms.PostgreSQL, ds, config.dataTable, JdbcAttrConfig.default.table)
        .unsafeRunSync()
      store
    },
    teardown = _ => ()
  )

  def h2AttrStore(
      dbms: Dbms,
      logger: Logger[IO],
      cfg: JdbcAttrConfig
  ): FunFixture[JdbcAttributeStore[IO]] = FunFixture(
    setup = { test =>
      val ds    = ConnectionConfig.h2Memory(test.name.replaceAll("\\s+", "_")).dataSource
      val store = JdbcAttributeStore[IO](cfg, ds, logger)
      store.runSetup(dbms).unsafeRunSync()
      store
    },
    teardown = _ => ()
  )

  def makeBinStore(
      cnt: JdbcDatabaseContainer,
      logger: Logger[IO],
      cfg: JdbcStoreConfig
  ): JdbcBinaryStore[IO] = {
    implicit val log = logger
    val cc           = ConnectionConfig(cnt.jdbcUrl, cnt.username, cnt.password)
    val store        = JdbcBinaryStore[IO](cc.dataSource, logger, cfg, JdbcAttrConfig.default)
    DatabaseSetup
      .runBoth[IO](
        Dbms.PostgreSQL,
        cc.dataSource,
        cfg.dataTable,
        JdbcAttrConfig.default.table
      )
      .unsafeRunSync()
    store
  }

}
