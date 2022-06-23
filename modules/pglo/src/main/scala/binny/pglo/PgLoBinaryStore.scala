package binny.pglo

import javax.sql.DataSource

import binny._
import binny.jdbc.JdbcBinaryStore
import binny.jdbc.impl.DataSourceResource
import binny.jdbc.impl.Implicits._
import binny.pglo.impl.PgApi
import binny.util.{Logger, RangeCalc, Stopwatch}
import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.implicits._
import fs2.{Pipe, Stream}

final class PgLoBinaryStore[F[_]: Sync](
    val config: PgLoConfig,
    logger: Logger[F],
    ds: DataSource
) extends JdbcBinaryStore[F] {
  private[this] val pg = new PgApi[F](config.table, logger)

  def insert: Pipe[F, Byte, BinaryId] =
    in =>
      Stream
        .eval(BinaryId.random)
        .flatMap(id => in.through(insertWith(id)) ++ Stream.emit(id))

  def insertWith(id: BinaryId): Pipe[F, Byte, Nothing] =
    bytes =>
      Stream.eval {
        for {
          insertTime <- Stopwatch.start[F]
          _ <- pg.insert(id, bytes.chunkN(config.chunkSize)).execute(ds)
          _ <- Stopwatch.show(insertTime)(d =>
            logger.trace(s"Inserting bytes for ${id.id} took: $d")
          )
          _ <- Stopwatch.show(insertTime)(d =>
            logger.debug(s"Inserting ${id.id} took: $d")
          )
        } yield ()
      }.drain

  def delete(id: BinaryId): F[Unit] =
    for {
      w <- Stopwatch.start[F]
      _ <- pg.delete(id).execute(ds)
      _ <- Stopwatch.show(w)(d => logger.info(s"Deleting ${id.id} took $d"))
    } yield ()

  /** Find the binary by its id. The byte stream is constructed to close resources to the
    * database after loading a chunk. This is for safety, since database timeouts can
    * occur if the stream "blocks". The `findBinaryStateful` is a variant that uses a
    * single connection for the entire stream.
    */
  def findBinary(
      id: BinaryId,
      range: ByteRange
  ): OptionT[F, Binary[F]] = {
    def bytes(oid: Long) =
      RangeCalc
        .calcChunks(range, config.chunkSize)
        .evalTap(r => logger.trace(s"Requesting range: $r"))
        .evalMap(r => pg.loadChunkByOID(oid, r).execute(ds))
        .takeThrough(_.size == config.chunkSize)
        .flatMap(Stream.chunk)

    OptionT(pg.findOid(id).execute(ds)).map(oid => bytes(oid))
  }

  def exists(id: BinaryId) =
    pg.exists(id).execute(ds).isDefined

  /** Finds the binary by its id and returns the bytes as a stream that uses a single
    * connection to the database. The connection is closed when the stream is terminated.
    */
  def findBinaryStateful(
      id: BinaryId,
      range: ByteRange
  ): OptionT[F, Binary[F]] =
    OptionT(pg.findOid(id).execute(ds)).map(_ => byteStream(id, range))

  private def byteStream(id: BinaryId, range: ByteRange): Stream[F, Byte] = {
    val data = pg.loadAll(id, range, config.chunkSize)
    Stream
      .resource(DataSourceResource(ds))
      .flatMap(data.run)
  }

  def computeAttr(id: BinaryId, hint: Hint): ComputeAttr[F] = Kleisli { select =>
    val attr =
      select match {
        case AttributeName.ContainsSha256(_) =>
          pg
            .exists(id)
            .flatMap(_ =>
              pg
                .computeAttr(id, config.detect, hint, config.chunkSize)
                .mapF(OptionT.liftF[F, BinaryAttributes])
            )
        case AttributeName.ContainsLength(_) =>
          pg.exists(id)
            .flatMap(_ =>
              pg.computeLength(id)
                .flatMap(len =>
                  pg.detectContentType(id, config.detect, hint)
                    .map(ct => BinaryAttributes(ct, len))
                )
                .mapF(OptionT.liftF[F, BinaryAttributes])
            )
        case _ =>
          pg.exists(id)
            .flatMap(_ =>
              pg.detectContentType(id, config.detect, hint)
                .map(ct => BinaryAttributes(ct, -1L))
                .mapF(OptionT.liftF[F, BinaryAttributes])
            )
      }

    for {
      w <- OptionT.liftF(Stopwatch.start[F])
      a <- attr.execute(ds)
      _ <- OptionT.liftF(
        Stopwatch.show(w)(d => logger.trace(s"Computing attributes took: $d"))
      )
    } yield a
  }

  def listIds(prefix: Option[String], chunkSize: Int): Stream[F, BinaryId] =
    pg.listAllIds(prefix, chunkSize, ds)
}

object PgLoBinaryStore {

  def apply[F[_]: Sync](
      config: PgLoConfig,
      logger: Logger[F],
      ds: DataSource
  ): PgLoBinaryStore[F] =
    new PgLoBinaryStore[F](config, logger, ds)

  def default[F[_]: Sync](logger: Logger[F], ds: DataSource): PgLoBinaryStore[F] =
    apply(PgLoConfig.default, logger, ds)
}
