package binny.pglo

import javax.sql.DataSource

import binny._
import binny.jdbc.JdbcBinaryStore
import binny.jdbc.impl.DataSourceResource
import binny.jdbc.impl.Implicits._
import binny.pglo.impl.PgApi
import binny.util.{Logger, RangeCalc, Stopwatch}
import cats.data.OptionT
import cats.effect._
import cats.implicits._
import fs2.{Pipe, Stream}

final class PgLoBinaryStore[F[_]: Sync](
    val config: PgLoConfig,
    logger: Logger[F],
    ds: DataSource,
    attrStore: BinaryAttributeStore[F]
) extends JdbcBinaryStore[F] {
  private[this] val pg = new PgApi[F](config.table, logger)

  def insert(hint: Hint): Pipe[F, Byte, BinaryId] =
    in =>
      Stream
        .eval(BinaryId.random)
        .flatMap(id => in.through(insertWith(id, hint)) ++ Stream.emit(id))

  def insertWith(id: BinaryId, hint: Hint): Pipe[F, Byte, Nothing] =
    bytes =>
      Stream.eval {
        for {
          insertTime <- Stopwatch.start[F]
          _ <- pg.insert(id, bytes.chunkN(config.chunkSize)).execute(ds)
          _ <- Stopwatch.show(insertTime)(d =>
            logger.trace(s"Inserting bytes for ${id.id} took: $d")
          )
          _ <- Stopwatch.wrap(d =>
            logger.trace(s"Inserting attributes for ${id.id} took: $d")
          ) {
            attrStore.saveAttr(
              id,
              pg.computeAttr(id, config.detect, hint, config.chunkSize).execute(ds)
            )
          }
          _ <- Stopwatch.show(insertTime)(d =>
            logger.debug(s"Inserting ${id.id} took: $d")
          )
        } yield ()
      }.drain

  def delete(id: BinaryId): F[Unit] =
    for {
      w <- Stopwatch.start[F]
      _ <- pg.delete(id).execute(ds)
      _ <- attrStore.deleteAttr(id)
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

  def computeAttr(id: BinaryId, hint: Hint): OptionT[F, BinaryAttributes] = {
    val attr =
      pg
        .exists(id)
        .flatMap(_ =>
          pg
            .computeAttr(id, config.detect, hint, config.chunkSize)
            .mapF(OptionT.liftF[F, BinaryAttributes])
        )

    for {
      w <- OptionT.liftF(Stopwatch.start[F])
      a <- attr.execute(ds)
      _ <- OptionT.liftF(
        Stopwatch.show(w)(d => logger.trace(s"Computing attributes took: $d"))
      )
    } yield a
  }

  def findAttr(id: BinaryId): OptionT[F, BinaryAttributes] =
    attrStore.findAttr(id)
}

object PgLoBinaryStore {

  def apply[F[_]: Sync](
      config: PgLoConfig,
      logger: Logger[F],
      ds: DataSource,
      attrStore: BinaryAttributeStore[F]
  ): PgLoBinaryStore[F] =
    new PgLoBinaryStore[F](config, logger, ds, attrStore)

  def default[F[_]: Sync](logger: Logger[F], ds: DataSource): PgLoBinaryStore[F] =
    apply(PgLoConfig.default, logger, ds, BinaryAttributeStore.empty[F])

}
