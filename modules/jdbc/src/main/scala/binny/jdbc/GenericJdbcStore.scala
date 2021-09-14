package binny.jdbc

import javax.sql.DataSource

import binny._
import binny.jdbc.impl.Implicits._
import binny.jdbc.impl.{DataSourceResource, DbRunApi}
import binny.util.{Logger, RangeCalc, Stopwatch}
import cats.data.OptionT
import cats.effect._
import cats.implicits._
import fs2.Pipe
import fs2.Stream

final class GenericJdbcStore[F[_]: Sync](
                                      ds: DataSource,
                                      logger: Logger[F],
                                      val config: JdbcStoreConfig,
                                      attrStore: BinaryAttributeStore[F]
                                    ) extends JdbcBinaryStore[F] {

  implicit private val log: Logger[F] = logger
  private[this] val dataApi = new DbRunApi[F](config.dataTable, logger)

  def insert(hint: ContentTypeDetect.Hint): Pipe[F, Byte, BinaryId] =
    in =>
      Stream
        .eval(BinaryId.random)
        .flatMap(id => in.through(insertWith(id, hint)) ++ Stream.emit(id))

  def insertWith(id: BinaryId, hint: ContentTypeDetect.Hint): Pipe[F, Byte, Nothing] =
    bytes =>
      {
        val inserts =
          dataApi
            .insertAllData(id, bytes.chunkN(config.chunkSize))
            .map(_.inTX)
            .compile
            .foldMonoid
            .flatMap(_.execute(ds))

        //in case this is the jdbc store; required for fk constraint
        val saveEmptyAttr =
          attrStore.saveAttr(id, Sync[F].pure(BinaryAttributes.empty))

        val saveAttr = {
          val ba = dataApi.computeAttr(id, config.detect, hint).execute(ds)
          for {
            w <- Stopwatch.start[F]
            _ <- attrStore.saveAttr(id, ba)
            _ <- Stopwatch.show(w)(d =>
              logger.trace(s"Computing and storing attributes for ${id.id} took $d")
            )
          } yield ()
        }

        for {
          _ <- logger.s.debug(s"Inserting data for id ${id.id}")
          w <- Stream.eval(Stopwatch.start[F])
          _ <- Stream.eval(saveEmptyAttr *> inserts *> saveAttr)
          _ <- Stream.eval(
            Stopwatch.show(w)(d => logger.debug(s"Inserting ${id.id} took $d"))
          )
        } yield ()
      }.drain

  /** Finds a binary by its id. The data stream loads the bytes chunk-wise from the
   * database, where on each chunk the connection to the database is closed. This is
   * safer when the stream is running for some time to avoid the server closing the
   * connection due to timeouts.
   */
  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]] =
    dataApi
      .exists(id)
      .execute(ds)
      .map(_ => dataStream(id, range))

  def findBinaryStateful(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]] =
    dataApi
      .exists(id)
      .execute(ds)
      .map(_ => dataStreamSingleConn(id, range))

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

  def delete(id: BinaryId): F[Unit] =
    for {
      w <- Stopwatch.start[F]
      _ <- dataApi.delete(id).inTX.execute(ds)
      _ <- attrStore.deleteAttr(id)
      _ <- Stopwatch.show(w)(d => logger.info(s"Deleting ${id.id} took $d"))
    } yield ()

  def findAttr(id: BinaryId): OptionT[F, BinaryAttributes] =
    attrStore.findAttr(id)
}

object GenericJdbcStore{
  def apply[F[_]: Sync](
                         ds: DataSource,
                         logger: Logger[F],
                         config: JdbcStoreConfig,
                         attrStore: BinaryAttributeStore[F]
                       ): JdbcBinaryStore[F] =
    new GenericJdbcStore[F](ds, logger, config, attrStore)

  def apply[F[_]: Sync](
                         ds: DataSource,
                         logger: Logger[F],
                         config: JdbcStoreConfig,
                         attrCfg: JdbcAttrConfig
                       ): JdbcBinaryStore[F] =
    new GenericJdbcStore[F](ds, logger, config, JdbcAttributeStore(attrCfg, ds, logger))

  def default[F[_]: Sync](ds: DataSource, logger: Logger[F]): JdbcBinaryStore[F] =
    apply(ds, logger, JdbcStoreConfig.default, JdbcAttrConfig.default)


}