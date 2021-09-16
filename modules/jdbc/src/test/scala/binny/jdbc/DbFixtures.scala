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
      db: String,
      logger: Logger[IO],
      config: JdbcStoreConfig
  ): JdbcBinaryStore[IO] = {
    implicit val log: Logger[IO] = logger
    val ds = ConnectionConfig.h2Memory(db.replaceAll("\\s+", "_")).dataSource
    val attrStore = JdbcAttributeStore(JdbcAttrConfig.default, ds, logger)
    val store = GenericJdbcStore[IO](ds, logger, config, attrStore)
    DatabaseSetup
      .runBoth[IO](Dbms.PostgreSQL, ds, config.dataTable, JdbcAttrConfig.default.table)
      .unsafeRunSync()
    store
  }

  def h2AttrStore(
      db: String,
      logger: Logger[IO],
      cfg: JdbcAttrConfig
  ): JdbcAttributeStore[IO] = {
    val ds = ConnectionConfig.h2Memory(db.replaceAll("\\s+", "_")).dataSource
    val store = JdbcAttributeStore[IO](cfg, ds, logger)
    store.runSetup(Dbms.H2).unsafeRunSync()
    store
  }

  def makeAttrStore(
      cnt: JdbcDatabaseContainer,
      logger: Logger[IO],
      cfg: JdbcAttrConfig
  ): JdbcAttributeStore[IO] = {
    val cc = ConnectionConfig(cnt.jdbcUrl, cnt.username, cnt.password)
    val ds = cc.dataSource
    implicit val log: Logger[IO] = logger
    DatabaseSetup
      .runAttr[IO](Dbms.unsafeFromJdbcUrl(cnt.jdbcUrl), ds, cfg.table)
      .unsafeRunSync()
    JdbcAttributeStore(cfg, ds, logger)
  }

  def makeBinStore(
      cnt: JdbcDatabaseContainer,
      logger: Logger[IO],
      cfg: JdbcStoreConfig
  ): JdbcBinaryStore[IO] = {
    implicit val log: Logger[IO] = logger

    val cc = ConnectionConfig(cnt.jdbcUrl, cnt.username, cnt.password)
    val ds = cc.dataSource
    val attrStore = JdbcAttributeStore(JdbcAttrConfig.default, ds, logger)
    val store = GenericJdbcStore[IO](cc.dataSource, logger, cfg, attrStore)
    DatabaseSetup
      .runBoth[IO](
        Dbms.unsafeFromJdbcUrl(cnt.jdbcUrl),
        cc.dataSource,
        cfg.dataTable,
        JdbcAttrConfig.default.table
      )
      .unsafeRunSync()
    store
  }
}
