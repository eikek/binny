package binny.pglo

import binny.jdbc._
import binny.util.Logger
import cats.effect._
import cats.effect.unsafe.implicits._
import com.dimafeng.testcontainers.JdbcDatabaseContainer

trait PgStoreFixtures {

  def makeBinStore(
      cnt: JdbcDatabaseContainer,
      logger: Logger[IO],
      cfg: PgLoConfig
  ): PgLoBinaryStore[IO] = {
    implicit val log = logger

    val cc = ConnectionConfig(cnt.jdbcUrl, cnt.username, cnt.password)
    val ds = cc.dataSource
    val attrStore = JdbcAttributeStore(JdbcAttrConfig.default, ds, logger)
    val store = PgLoBinaryStore[IO](cfg, logger, cc.dataSource, attrStore)
    DatabaseSetup
      .runAttr[IO](
        Dbms.PostgreSQL,
        cc.dataSource,
        JdbcAttrConfig.default.table
      )
      .unsafeRunSync()
    PgSetup.run[IO](cfg.table, logger, ds).unsafeRunSync()
    store
  }

}

object PgStoreFixtures extends PgStoreFixtures
