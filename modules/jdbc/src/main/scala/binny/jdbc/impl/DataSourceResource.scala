package binny.jdbc.impl

import java.sql.Connection
import javax.sql.DataSource

import cats.effect._

object DataSourceResource {
  def apply[F[_]: Sync](ds: DataSource): Resource[F, Connection] =
    Resource.make(Sync[F].blocking(ds.getConnection))(conn =>
      Sync[F].blocking(conn.close())
    )
}
