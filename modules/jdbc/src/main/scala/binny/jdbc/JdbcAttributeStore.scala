package binny.jdbc

import binny._
import binny.jdbc.impl.{DbRun, DbRunApi}
import binny.util.Logger
import cats.data.OptionT
import cats.effect._
import binny.jdbc.impl.Implicits._

import javax.sql.DataSource

final class JdbcAttributeStore[F[_]: Sync](
    table: String,
    ds: DataSource,
    logger: Logger[F]
) extends BinaryAttributeStore[F] {
  private[this] val dbApi = new DbRunApi[F](table, logger)

  implicit private val log = logger

  def runSetup(dbms: Dbms): F[Int] =
    DatabaseSetup.runAttr[F](dbms, ds, table)

  def saveAttr(id: BinaryId, attrs: BinaryAttributes): F[Unit] =
    (for {
      n <- dbApi.updateAttr(id, attrs)
      _ <- if (n > 0) DbRun.pure(n) else dbApi.insertAttr(id, attrs)
    } yield ()).execute(ds)

  def deleteAttr(id: BinaryId): F[Boolean] =
    dbApi.delete(id).map(_ > 0).execute(ds)

  def findAttr(id: BinaryId): OptionT[F, BinaryAttributes] =
    OptionT(dbApi.queryAttr(id).execute(ds))
}

object JdbcAttributeStore {
  def apply[F[_]: Sync](
      table: String,
      ds: DataSource,
      logger: Logger[F]
  ): JdbcAttributeStore[F] =
    new JdbcAttributeStore[F](table, ds, logger)
}
