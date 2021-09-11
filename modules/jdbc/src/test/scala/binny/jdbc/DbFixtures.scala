package binny.jdbc

import binny.util.Logger
import cats.effect.IO
import com.dimafeng.testcontainers.JdbcDatabaseContainer
import munit.CatsEffectSuite

import javax.sql.DataSource

trait DbFixtures { self: CatsEffectSuite =>

  def h2MemoryDataSource: FunFixture[DataSource] = FunFixture(
    setup = test => {
      ConnectionConfig.h2Memory(test.name.replaceAll("\\s+", "_")).dataSource
    },
    teardown = _ => ()
  )

  def h2BinStore(
      dbms: Dbms,
      logger: Logger[IO],
      config: JdbcStoreConfig
  ): FunFixture[JdbcBinaryStore[IO]] = FunFixture(
    setup = { test =>
      val ds = ConnectionConfig.h2Memory(test.name.replaceAll("\\s+", "_")).dataSource
      val store = JdbcBinaryStore[IO](ds, logger, config)
      store.runSetup(dbms).unsafeRunSync()
      store
    },
    teardown = _ => ()
  )

  def h2AttrStore(
      dbms: Dbms,
      logger: Logger[IO],
      table: String
  ): FunFixture[JdbcAttributeStore[IO]] = FunFixture(
    setup = { test =>
      val ds = ConnectionConfig.h2Memory(test.name.replaceAll("\\s+", "_")).dataSource
      val store = JdbcAttributeStore[IO](table, ds, logger)
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
    val cc    = ConnectionConfig(cnt.jdbcUrl, cnt.username, cnt.password)
    val store = JdbcBinaryStore[IO](cc.dataSource, logger, cfg)
    store.runSetup(Dbms.PostgreSQL).unsafeRunSync()
    store
  }

}
