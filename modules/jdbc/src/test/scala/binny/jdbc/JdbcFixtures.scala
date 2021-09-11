package binny.jdbc

import cats.effect._
import munit.{FunSuite}

trait JdbcFixtures { self: FunSuite =>

  def jdbcStore(
      cc: ConnectionConfig,
      storeCfg: JdbcStoreConfig
  ): FunFixture[JdbcBinaryStore[IO]] =
    FunFixture(
      setup = { _ =>
        val ds = cc.dataSource
        JdbcBinaryStore[IO](ds, storeCfg)
      },
      teardown = _ => ()
    )

}
