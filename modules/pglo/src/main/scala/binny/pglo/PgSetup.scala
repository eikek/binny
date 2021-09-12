package binny.pglo

import binny.pglo.impl.PgApi
import binny.jdbc.impl.Implicits._
import binny.util.Logger
import cats.effect.kernel.Sync

import javax.sql.DataSource

object PgSetup {

  def run[F[_]: Sync](table: String, logger: Logger[F], ds: DataSource): F[Int] =
    new PgApi[F](table, logger).createTable.execute(ds)
}
