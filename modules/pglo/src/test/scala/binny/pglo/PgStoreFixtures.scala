package binny.pglo

import binny.jdbc._
import binny.jdbc.impl.Implicits._
import binny.pglo.impl.PgApi
import binny.util.Logger
import cats.effect._
import cats.effect.unsafe.implicits._

trait PgStoreFixtures {

  def makeBinStore(
      logger: Logger[IO],
      cfg: PgLoConfig
  ): PgLoBinaryStore[IO] = {
    val cc = ConnectionConfig.Postgres.default
    val ds = cc.dataSource
    val store = PgLoBinaryStore[IO](cfg, logger, cc.dataSource)

    val pg = new PgApi[IO](cfg.table, logger)
    pg.createTable.flatMap(_ => pg.truncateTable).execute(ds).unsafeRunSync()

    store
  }
}

object PgStoreFixtures extends PgStoreFixtures
