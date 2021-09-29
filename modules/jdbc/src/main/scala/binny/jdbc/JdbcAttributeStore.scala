package binny.jdbc

import javax.sql.DataSource

import binny._
import binny.jdbc.impl.Implicits._
import binny.jdbc.impl.{DbRun, DbRunApi}
import binny.util.Logger
import cats.data.OptionT
import cats.effect._
import cats.implicits._

final class JdbcAttributeStore[F[_]: Sync](
    val config: JdbcAttrConfig,
    ds: DataSource,
    logger: Logger[F]
) extends BinaryAttributeStore[F] {
  private[this] val dbApi = new DbRunApi[F](config.table, logger)

  implicit private val log: Logger[F] = logger

  def runSetup(dbms: Dbms): F[Int] =
    DatabaseSetup.runAttr[F](dbms, ds, config.table)

  def saveAttr(id: BinaryId, attrs: F[BinaryAttributes]): F[Unit] = {
    def store(a: BinaryAttributes): F[Unit] =
      (for {
        insertRes <- dbApi.insertAttr(id, a).attempt
        _ <- insertRes match {
          case Left(_) =>
            dbApi.updateAttr(id, a)
          case Right(n) =>
            DbRun.pure[F, Int](n)
        }
      } yield ()).execute(ds)
    attrs.flatMap(store)
  }

  def deleteAttr(id: BinaryId): F[Boolean] =
    dbApi.delete(id).map(_ > 0).execute(ds)

  def findAttr(id: BinaryId): OptionT[F, BinaryAttributes] =
    OptionT(dbApi.queryAttr(id).execute(ds))
}

object JdbcAttributeStore {
  def apply[F[_]: Sync](
      config: JdbcAttrConfig,
      ds: DataSource,
      logger: Logger[F]
  ): JdbcAttributeStore[F] =
    new JdbcAttributeStore[F](config, ds, logger)
}
