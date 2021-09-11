package binny.jdbc

import javax.sql.DataSource
import binny._
import binny.jdbc.impl.{DbRun, DbRunApi}
import binny.jdbc.impl.Implicits._
import binny.util.{Logger, Stopwatch}
import cats.Applicative
import cats.data.OptionT
import cats.effect._
import cats.implicits._
import fs2.Stream

final class JdbcBinaryStore[F[_]: Async](
    ds: DataSource,
    logger: Logger[F],
    val config: JdbcStoreConfig
) extends BinaryStore[F]
    with ReadonlyAttributeStore[F] {

  implicit private val log  = logger
  private[this] val dataApi = new DbRunApi[F](config.dataTable, logger)
  private[this] val attrApi =
    config.metaTable.map(mt => new DbRunApi[F](mt, logger))

  def runSetup(dbms: Dbms): F[Int] =
    DatabaseSetup.run[F](dbms, ds, config)

  def insertWith(data: BinaryData[F], hint: ContentTypeDetect.Hint): F[Unit] = {
    val attrInit =
      attrApi.map(_.insertEmptyAttr(data.id)).getOrElse(DbRun.pure[F, Int](0))

    val inserts =
      dataApi
        .insertAllData(data.id, data.bytes.chunkN(config.chunkSize))
        .map(_.inTX)
        .compile
        .foldMonoid
        .flatMap(_.execute(ds))

    val saveAttr = attrApi.map { api =>
      for {
        w <- Stopwatch.start[F]
        ba <- dataStream(data.id)
          .through(BinaryAttributes.compute(config.detect, hint))
          .compile
          .lastOrError
        task = api.updateAttr(data.id, ba)
        _ <- task.execute(ds)
        _ <- Stopwatch.show(w)(d =>
          logger.trace(s"Computing and storing attributes for ${data.id.id} took $d")
        )
      } yield ()
    }

    for {
      _ <- logger.debug(s"Inserting data for id ${data.id.id}")
      w <- Stopwatch.start[F]
      _ <- attrInit.execute(ds)
      _ <- inserts
      _ <- saveAttr.getOrElse(Applicative[F].pure(0))
      _ <- Stopwatch.show(w)(d => logger.debug(s"Inserting ${data.id.id} took $d"))
    } yield ()
  }

  def delete(id: BinaryId): F[Boolean] =
    for {
      _ <- logger.info(s"Deleting ${id.id}")
      w <- Stopwatch.start[F]
      n <- dataApi.delete(id).inTX.execute(ds)
      _ <- attrApi.map(_.delete(id).inTX.execute(ds)).getOrElse(0.pure[F])
      _ <- Stopwatch.show(w)(d => logger.info(s"Deleting ${id.id} took $d"))
    } yield n > 0

  private def dataStream(id: BinaryId) =
    Stream
      .iterate(0)(_ + 1)
      .map(n => dataApi.queryChunk(id, n))
      .covary[F]
      .evalMap(_.execute(ds))
      .unNoneTerminate
      .flatMap(Stream.chunk)

  def load(id: BinaryId, range: ByteRange, chunkSize: Int): OptionT[F, BinaryData[F]] =
    OptionT(
      dataApi
        .exists(id)
        .execute(ds)
        .map(exists => if (exists) Some(BinaryData[F](id, dataStream(id))) else None)
    )

  def findAttr(id: BinaryId): OptionT[F, BinaryAttributes] =
    attrApi match {
      case Some(api) =>
        OptionT(api.queryAttr(id).execute(ds))
      case None =>
        OptionT.none
    }
}

object JdbcBinaryStore {

  def apply[F[_]: Async](
      ds: DataSource,
      logger: Logger[F],
      config: JdbcStoreConfig
  ): JdbcBinaryStore[F] =
    new JdbcBinaryStore[F](ds, logger, config)
}
