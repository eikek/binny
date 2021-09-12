package binny.pglo

import javax.sql.DataSource

import binny.jdbc.impl.Implicits._
import binny.pglo.impl.PgApi
import binny.util.Logger
import cats.effect.kernel.Sync

object PgSetup {

  def run[F[_]: Sync](table: String, logger: Logger[F], ds: DataSource): F[Int] =
    new PgApi[F](table, logger).createTable.execute(ds)
}
