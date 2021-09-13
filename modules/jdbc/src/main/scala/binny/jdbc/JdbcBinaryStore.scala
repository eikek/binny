package binny.jdbc

import javax.sql.DataSource

import binny._
import binny.jdbc.impl.Implicits._
import binny.jdbc.impl.{DataSourceResource, DbRunApi}
import binny.util.{Logger, RangeCalc, Stopwatch}
import cats.data.OptionT
import cats.effect._
import cats.implicits._
import fs2.Stream

final class JdbcBinaryStore[F[_]: Sync](
    ds: DataSource,
    logger: Logger[F],
    val config: JdbcStoreConfig,
    attrStore: BinaryAttributeStore[F]
) extends BinaryStore[F] {

  implicit private val log: Logger[F] = logger
  private[this] val dataApi = new DbRunApi[F](config.dataTable, logger)

  def insertWith(data: BinaryData[F], hint: ContentTypeDetect.Hint): F[Unit] = {
    val inserts =
      dataApi
        .insertAllData(data.id, data.bytes.chunkN(config.chunkSize))
        .map(_.inTX)
        .compile
        .foldMonoid
        .flatMap(_.execute(ds))

    val saveEmptyAttr =
      attrStore.saveAttr(data.id, Sync[F].pure(BinaryAttributes.empty))

    val saveAttr = {
      val ba = dataApi.computeAttr(data.id, config.detect, hint).execute(ds)
      for {
        w <- Stopwatch.start[F]
        _ <- attrStore.saveAttr(data.id, ba)
        _ <- Stopwatch.show(w)(d =>
          logger.trace(s"Computing and storing attributes for ${data.id.id} took $d")
        )
      } yield ()
    }

    for {
      _ <- logger.debug(s"Inserting data for id ${data.id.id}")
      w <- Stopwatch.start[F]
      _ <- saveEmptyAttr //in case this is the jdbc store; required for fk constraint
      _ <- inserts
      _ <- saveAttr
      _ <- Stopwatch.show(w)(d => logger.debug(s"Inserting ${data.id.id} took $d"))
    } yield ()
  }

  def delete(id: BinaryId): F[Boolean] =
    for {
      w <- Stopwatch.start[F]
      n <- dataApi.delete(id).inTX.execute(ds)
      _ <- attrStore.deleteAttr(id)
      _ <- Stopwatch.show(w)(d => logger.info(s"Deleting ${id.id} took $d"))
    } yield n > 0

  private def dataStream(id: BinaryId, range: ByteRange) =
    range match {
      case ByteRange.All =>
        Stream
          .iterate(0)(_ + 1)
          .map(n => dataApi.queryChunk(id, n))
          .covary[F]
          .evalMap(_.execute(ds))
          .unNoneTerminate
          .flatMap(Stream.chunk)
      case ByteRange.Chunk(_, _) =>
        val offsets = RangeCalc.calcOffset(range, config.chunkSize)
        Stream
          .iterate(offsets.firstChunk)(_ + 1)
          .take(offsets.takeChunks)
          .covary[F]
          .evalMap(n =>
            dataApi
              .queryChunk(id, n)
              .execute(ds)
              .map(_.map(c => RangeCalc.chop(c, offsets, n)))
          )
          .unNoneTerminate
          .flatMap(Stream.chunk)
    }

  private def dataStreamSingleConn(id: BinaryId, range: ByteRange): Stream[F, Byte] =
    Stream
      .resource(DataSourceResource[F](ds))
      .flatMap(dataApi.queryAll(id, range, config.chunkSize).run)

  /** Finds a binary by its id. The data stream loads the bytes chunk-wise from the
    * database, where on each chunk the connection to the database is closed. This is
    * safer when the stream is running for some time to avoid the server closing the
    * connection due to timeouts.
    */
  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, BinaryData[F]] =
    dataApi
      .exists(id)
      .execute(ds)
      .map(_ => BinaryData[F](id, dataStream(id, range)))

  /** Finds a binary by its id. This uses a single connection for the entire byte stream.
    * Thus the connection is closed when the stream terminates. This is useful for small
    * files or when only a small portion of a file is requested.
    */
  def findBinaryStateful(id: BinaryId, range: ByteRange): OptionT[F, BinaryData[F]] =
    dataApi
      .exists(id)
      .execute(ds)
      .map(_ => BinaryData[F](id, dataStreamSingleConn(id, range)))

  def findAttr(id: BinaryId): OptionT[F, BinaryAttributes] =
    attrStore.findAttr(id)
}

object JdbcBinaryStore {

  def apply[F[_]: Sync](
      ds: DataSource,
      logger: Logger[F],
      config: JdbcStoreConfig,
      attrStore: BinaryAttributeStore[F]
  ): JdbcBinaryStore[F] =
    new JdbcBinaryStore[F](ds, logger, config, attrStore)

  def apply[F[_]: Sync](
      ds: DataSource,
      logger: Logger[F],
      config: JdbcStoreConfig,
      attrCfg: JdbcAttrConfig
  ): JdbcBinaryStore[F] =
    new JdbcBinaryStore[F](ds, logger, config, JdbcAttributeStore(attrCfg, ds, logger))

  def default[F[_]: Sync](ds: DataSource, logger: Logger[F]): JdbcBinaryStore[F] =
    apply(ds, logger, JdbcStoreConfig.default, JdbcAttrConfig.default)
}
