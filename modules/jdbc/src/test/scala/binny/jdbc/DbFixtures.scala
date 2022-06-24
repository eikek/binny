package binny.jdbc

import javax.sql.DataSource

import binny.jdbc.impl.CreateDataTable
import binny.jdbc.impl.Implicits._
import binny.util.Logger
import cats.effect.IO
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
      connectionConfig: ConnectionConfig,
      logger: Logger[IO],
      cfg: JdbcStoreConfig,
      createSchema: Boolean
  ): GenericJdbcStore[IO] = {
    implicit val log: Logger[IO] = logger

    val store = GenericJdbcStore[IO](connectionConfig.dataSource, logger, cfg)
    if (createSchema) {
      val dbms = Dbms.unsafeFromJdbcUrl(connectionConfig.url)
      val dd = CreateDataTable[IO](cfg.dataTable)
      dd.createData(dbms)
        .flatMap(_ => dd.truncate)
        .execute(connectionConfig.dataSource)
        .unsafeRunSync()
    }
    store
  }
}
