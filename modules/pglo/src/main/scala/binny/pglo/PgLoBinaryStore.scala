package binny.pglo

import javax.sql.DataSource

import binny._
import binny.jdbc.impl.DataSourceResource
import binny.jdbc.impl.Implicits._
import binny.pglo.impl.PgApi
import binny.util.{Logger, RangeCalc, Stopwatch}
import cats.data.OptionT
import cats.effect._
import cats.implicits._
import fs2.Stream

final class PgLoBinaryStore[F[_]: Sync](
    val config: PgLoConfig,
    logger: Logger[F],
    ds: DataSource,
    attrStore: BinaryAttributeStore[F]
) extends BinaryStore[F] {
  private[this] val pg = new PgApi[F](config.table, logger)

  def insertWith(data: BinaryData[F], hint: ContentTypeDetect.Hint): F[Unit] =
    for {
      insertTime <- Stopwatch.start[F]
      _ <- pg.insert(data.id, data.bytes.chunkN(config.chunkSize)).execute(ds)
      _ <- Stopwatch.show(insertTime)(d =>
        logger.trace(s"Inserting bytes for ${data.id.id} took: $d")
      )
      _ <- Stopwatch.wrap(d =>
        logger.trace(s"Inserting attributes for ${data.id.id} took: $d")
      ) {
        attrStore.saveAttr(
          data.id,
          pg.computeAttr(data.id, config.detect, hint, config.chunkSize).execute(ds)
        )
      }
      _ <- Stopwatch.show(insertTime)(d =>
        logger.debug(s"Inserting ${data.id.id} took: $d")
      )
    } yield ()

  def delete(id: BinaryId): F[Boolean] =
    for {
      w <- Stopwatch.start[F]
      n <- pg.delete(id).execute(ds)
      _ <- attrStore.deleteAttr(id)
      _ <- Stopwatch.show(w)(d => logger.info(s"Deleting ${id.id} took $d"))
    } yield n > 0

  /** Find the binary by its id. The byte stream is constructed to close resources to the
    * database after loading a chunk. This is for safety, since database timeouts can
    * occur if the stream "blocks". The `findBinaryStateful` is a variant that uses a
    * single connection for the entire stream.
    */
  def findBinary(
      id: BinaryId,
      range: ByteRange
  ): OptionT[F, BinaryData[F]] = {
    def bytes(oid: Long) =
      RangeCalc
        .calcChunks(range, config.chunkSize)
        .evalTap(r => logger.trace(s"Requesting range: $r"))
        .evalMap(r => pg.loadChunkByOID(oid, r).execute(ds))
        .takeThrough(_.size == config.chunkSize)
        .flatMap(Stream.chunk)

    OptionT(pg.findOid(id).execute(ds)).map(oid => BinaryData(id, bytes(oid)))
  }

  /** Finds the binary by its id and returns the bytes as a stream that uses a single
    * connection to the database. The connection is closed when the stream is terminated.
    */
  def findBinaryStateful(
      id: BinaryId,
      range: ByteRange
  ): OptionT[F, BinaryData[F]] =
    OptionT(pg.findOid(id).execute(ds)).map(_ => BinaryData(id, byteStream(id, range)))

  private def byteStream(id: BinaryId, range: ByteRange): Stream[F, Byte] = {
    val data = pg.loadAll(id, range, config.chunkSize)
    Stream
      .resource(DataSourceResource(ds))
      .flatMap(data.run)
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
