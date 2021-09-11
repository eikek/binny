package binny.jdbc

import cats.effect._

import java.sql.Connection
import javax.sql.DataSource
import scala.util.{Using}

package object impl {
  type DbRun[+A] = Connection => A

  object DbRun extends DbRunApi {
    def apply[A](f: Connection => A): DbRun[A] = f

    implicit final class DbRunOps[A](dbRun: DbRun[A]) {
      import cats.implicits._

      def inTX: DbRun[A] =
        (for {
          _   <- DbRun.setAutoCommit(false)
          res <- dbRun
          _   <- DbRun.commit
        } yield res).onError({ case _ => DbRun.rollback })

      def execute[F[_]: Sync](ds: DataSource): F[A] =
        Sync[F].blocking {
          Using.resource(ds.getConnection)(dbRun)
        }
    }
  }
}
