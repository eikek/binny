package binny.jdbc

import javax.sql.DataSource

import binny._
import binny.jdbc.impl.DbRunApi.ChunkInfo
import binny.jdbc.impl.Implicits._
import binny.jdbc.impl.{DataSourceResource, DbRunApi}
import binny.util.RangeCalc.Offsets
import binny.util.{Logger, RangeCalc, Stopwatch}
import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.implicits._
import fs2.{Chunk, Pipe, Stream}
import scodec.bits.ByteVector

trait GenericJdbcStore[F[_]] extends JdbcBinaryStore[F] with ChunkedBinaryStore[F] {}

object GenericJdbcStore {
  def apply[F[_]: Sync](
      ds: DataSource,
      logger: Logger[F],
      config: JdbcStoreConfig
  ): GenericJdbcStore[F] =
    new Impl[F](ds, logger, config)

  def default[F[_]: Sync](ds: DataSource, logger: Logger[F]): GenericJdbcStore[F] =
    apply(ds, logger, JdbcStoreConfig.default)

  // -- impl

  final private class Impl[F[_]: Sync](
      ds: DataSource,
      logger: Logger[F],
      val config: JdbcStoreConfig
  ) extends GenericJdbcStore[F] {

    implicit private val log: Logger[F] = logger
    private[this] val dataApi = new DbRunApi[F](config.dataTable, logger)

    def insert: Pipe[F, Byte, BinaryId] =
      in =>
        Stream
          .eval(BinaryId.random)
          .flatMap(id => in.through(insertWith(id)) ++ Stream.emit(id))

    def insertWith(id: BinaryId): Pipe[F, Byte, Nothing] =
      bytes =>
        {
          val inserts =
            dataApi
              .insertAllData(id, bytes.chunkN(config.chunkSize))
              .map(_.inTX)
              .compile
              .foldMonoid
              .flatMap(_.execute(ds))

          for {
            _ <- logger.s.debug(s"Inserting data for id ${id.id}")
            w <- Stream.eval(Stopwatch.start[F])
            _ <- Stream.eval(inserts)
            _ <- Stream.eval(
              Stopwatch.show(w)(d => logger.debug(s"Inserting ${id.id} took $d"))
            )
          } yield ()
        }.drain

    def insertChunk(
        id: BinaryId,
        chunkDef: ChunkDef,
        hint: Hint,
        data: ByteVector
    ): F[InsertChunkResult] =
      InsertChunkResult.validateChunk(
        chunkDef,
        config.chunkSize,
        data.length.toInt
      ) match {
        case Some(bad) => bad.pure[F]
        case None =>
          val len = data.length
          val ch = chunkDef.fold(identity, _.toTotal(config.chunkSize))
          val insert = dataApi
            .insertNextChunk(id, ch.index, ch.total, Chunk.byteVector(data))
            .execute(ds)

          logger.trace(s"Insert chunk ${ch.index + 1}/${ch.total} of size $len") *>
            insert
      }

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

    def exists(id: BinaryId) =
      dataApi.exists(id).execute(ds).isDefined

    def findBinaryStateful(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]] =
      dataApi
        .exists(id)
        .execute(ds)
        .map(_ => dataStreamSingleConn(id, range))

    private def dataStream(id: BinaryId, range: ByteRange): Binary[F] =
      range match {
        case ByteRange.All =>
          Stream
            .iterate(0)(_ + 1)
            .map(n => dataApi.queryChunk(id, n))
            .covary[F]
            .evalMap(_.execute(ds))
            .unNoneTerminate
            .flatMap(Stream.chunk)

        case ByteRange.Chunk(skip, len) =>
          Stream.eval(dataApi.getChunkSize(id).execute(ds)).flatMap {
            case ChunkInfo.None =>
              Binary.empty[F]

            case ChunkInfo.Single =>
              val offsets = Offsets(0, 1, skip.toInt, len)
              Stream
                .eval(
                  dataApi
                    .queryChunk(id, 0)
                    .execute(ds)
                    .map(_.map(c => RangeCalc.chop(c, offsets, 0)))
                    .map(_.getOrElse(Chunk.empty[Byte]))
                )
                .flatMap(Stream.chunk)

            case ChunkInfo.Multiple(chunkSize) =>
              val offsets = RangeCalc.calcOffset(range, chunkSize)
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
      }

    private def dataStreamSingleConn(id: BinaryId, range: ByteRange): Binary[F] =
      Stream
        .resource(DataSourceResource[F](ds))
        .flatMap(dataApi.queryAll(id, range).run)

    def delete(id: BinaryId): F[Unit] =
      for {
        w <- Stopwatch.start[F]

        // When deleting large files, doing it in one transaction may blow memory.
        r <- Stream
          .iterate(0)(_ + 1)
          .evalMap(n => dataApi.deleteChunk(id, n).execute(ds))
          .takeWhile(_ > 0)
          .compile
          .foldMonoid

        _ <- Stopwatch.show(w)(d =>
          logger.info(s"Deleting $r chunks of ${id.id} took $d")
        )
      } yield ()

    def computeAttr(id: BinaryId, hint: Hint): ComputeAttr[F] =
      Kleisli { select =>
        val attr =
          select match {
            case AttributeName.ContainsSha256(_) =>
              dataApi
                .exists(id)
                .flatMap(_ =>
                  dataApi
                    .computeAttrAll(id, config.detect, hint)
                    .mapF(OptionT.liftF[F, BinaryAttributes])
                )
            case AttributeName.ContainsLength(_) =>
              dataApi
                .exists(id)
                .flatMap(_ =>
                  dataApi
                    .computeAttrLen(id)
                    .flatMap(len =>
                      dataApi
                        .computeAttrDetect(id, config.detect, hint)
                        .map(ct => BinaryAttributes(ct, len))
                    )
                    .mapF(OptionT.liftF[F, BinaryAttributes])
                )

            case _ =>
              dataApi
                .exists(id)
                .flatMap(_ =>
                  dataApi
                    .computeAttrDetect(id, config.detect, hint)
                    .map(ct => BinaryAttributes(ct, -1))
                    .mapF(OptionT.liftF[F, BinaryAttributes])
                )
          }

        for {
          w <- OptionT.liftF(Stopwatch.start[F])
          a <- attr.execute(ds)
          _ <- OptionT.liftF(
            Stopwatch.show(w)(d => logger.debug(s"Computing attributes took: $d"))
          )
        } yield a
      }

    def listIds(prefix: Option[String], chunkSize: Int): Stream[F, BinaryId] =
      dataApi.listAllIds(prefix, chunkSize, ds)
  }
}
