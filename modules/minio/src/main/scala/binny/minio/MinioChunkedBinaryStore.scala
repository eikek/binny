package binny.minio

import binny._
import binny.util.RangeCalc.Offsets
import binny.util.{Logger, RangeCalc, StreamUtil}
import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.syntax.all._
import fs2.{Pipe, Stream}
import io.minio.MinioAsyncClient
import scodec.bits.ByteVector

final class MinioChunkedBinaryStore[F[_]: Async](
    val config: MinioConfig,
    private[minio] val client: MinioAsyncClient,
    logger: Logger[F]
) extends ChunkedBinaryStore[F] {
  private[this] val minio = new Minio[F](client)
  val keyMapping = S3KeyMapping(
    id =>
      config.keyMapping
        .makeS3Key(id)
        .changeObjectName(n => objectName(n, 0)),
    config.keyMapping.bucketFilter
  )

  private[this] val minioStore =
    MinioBinaryStore[F](config.withKeyMapping(keyMapping), client, logger)

  def insertChunk(
      id: BinaryId,
      chunkDef: ChunkDef,
      hint: Hint,
      data: ByteVector
  ) =
    InsertChunkResult.validateChunk(chunkDef, config.chunkSize, data.length.toInt) match {
      case Some(bad) => bad.pure[F]
      case None      =>
        val ch = chunkDef.fold(identity, _.toTotal(config.chunkSize))
        val s3Key =
          config.keyMapping.makeS3Key(id).changeObjectName(n => objectName(n, ch.index))
        val insert =
          minio.uploadObject(s3Key, config.partSize, config.detect, data)

        val checkComplete =
          listChunks(id, max = 100).compile.count
            .map(chunks =>
              if (chunks >= ch.total) InsertChunkResult.complete
              else InsertChunkResult.incomplete
            )

        for {
          _ <- logger.trace(
            s"Insert chunk ${ch.index + 1}/${ch.total} of size ${data.length}"
          )
          _ <- insert
          r <- checkComplete
        } yield r
    }

  def computeAttr(id: BinaryId, hint: Hint): ComputeAttr[F] = Kleisli { select =>
    val first = keyMapping.makeS3Key(id)
    OptionT(minio.statObject(first).attempt.map(_.toOption)).semiflatMap { stat =>
      select match {
        case AttributeName.ContainsSha256(_) =>
          generateS3Keys(id, Offsets.none)
            .map(_._1)
            .through(loadChunkFiles)
            .through(ComputeAttr.computeAll(config.detect, hint))
            .compile
            .last
            .map(_.getOrElse(BinaryAttributes.empty))

        case AttributeName.ContainsLength(_) =>
          val len =
            generateS3Keys(id, Offsets.none)
              .map(_._1)
              .evalMap(key => minio.statObject(key).attempt.map(_.toOption))
              .unNoneTerminate
              .compile
              .fold(0L)(_ + _.size())

          (minio.detectContentType(first, stat.some, config.detect, hint), len)
            .mapN(BinaryAttributes.apply)
        case _ =>
          minio
            .detectContentType(first, stat.some, config.detect, hint)
            .map(c => BinaryAttributes(c, -1L))
      }
    }
  }

  def listIds(prefix: Option[String], chunkSize: Int): Stream[F, BinaryId] =
    Stream.eval(logger.info(s"List ids with filter: $prefix")) *>
      minio
        .listBuckets()
        .filter(config.keyMapping.bucketFilter)
        .flatMap(bucket => minio.listObjects(bucket, None, 100, prefix))
        .map(idFromObjectName)

  def insert = minioStore.insert

  def insertWith(id: BinaryId) = minioStore.insertWith(id)

  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]] = {
    val list = listChunks(id, max = 2).take(2).compile.toList
    OptionT.liftF(list).flatMap {
      case Nil         => OptionT.none[F, Binary[F]]
      case name :: Nil =>
        val key = S3Key(keyMapping.makeS3Key(id).bucket, name)
        OptionT.pure(minio.getObjectAsStream(key, config.chunkSize, range))
      case _ =>
        val offsets = RangeCalc.calcOffset(range, config.chunkSize)
        val allChunks = generateS3Keys(id, offsets)
        if (offsets.isNone) {
          OptionT.pure(allChunks.map(_._1).through(loadChunkFiles))
        } else {
          OptionT.pure(
            allChunks
              .flatMap { case (s3Key, index) =>
                RangeCalc.chopOffsets(offsets, index.toInt) match {
                  case (Some(start), Some(end)) =>
                    val br = ByteRange.Chunk(start, start + end)
                    Stream.eval(
                      minio.getObjectAsStreamOption(s3Key, config.chunkSize, br)
                    )

                  case (Some(start), None) =>
                    val br = ByteRange.Chunk(start, Int.MaxValue)
                    Stream.eval(
                      minio.getObjectAsStreamOption(s3Key, config.chunkSize, br)
                    )

                  case (None, Some(end)) =>
                    val br = ByteRange.Chunk(0, end)
                    Stream.eval(
                      minio.getObjectAsStreamOption(s3Key, config.chunkSize, br)
                    )

                  case (None, None) =>
                    val br = ByteRange.All
                    Stream.eval(
                      minio.getObjectAsStreamOption(s3Key, config.chunkSize, br)
                    )
                }
              }
              .unNoneTerminate
              .flatten
          )
        }
    }
  }

  def exists(id: BinaryId): F[Boolean] = {
    val key = keyMapping.makeS3Key(id)
    minio.exists(key)
  }

  def delete(id: BinaryId): F[Unit] = {
    val bucket = keyMapping.makeS3Key(id).bucket
    listChunks(id, 100)
      .parEvalMap(2)(key => minio.deleteObject(S3Key(bucket, key)))
      .compile
      .drain
  }

  private def listChunks(id: BinaryId, max: Int) = {
    val templateKey = config.keyMapping.makeS3Key(id)
    val bucket = templateKey.bucket
    val prefix = s"${templateKey.objectName}/chunk_"
    minio.listObjects(bucket, None, max, Some(prefix))
  }

  private def makeS3Key(id: BinaryId): Pipe[F, Int, S3Key] = {
    val templateKey = config.keyMapping.makeS3Key(id)
    _.map(n => templateKey.changeObjectName(name => objectName(name, n)))
  }

  private def generateS3Keys(id: BinaryId, offsets: Offsets): Stream[F, (S3Key, Long)] =
    if (offsets.isNone)
      Stream
        .iterate(0)(_ + 1)
        .through(makeS3Key(id))
        .zipWithIndex
    else
      Stream
        .iterate(offsets.firstChunk)(_ + 1)
        .take(offsets.takeChunks)
        .through(makeS3Key(id))
        .through(StreamUtil.zipWithIndexFrom(offsets.firstChunk))

  private def loadChunkFiles: Pipe[F, S3Key, Byte] =
    _.flatMap(s3Key =>
      Stream.eval(
        minio.getObjectAsStreamOption(s3Key, config.chunkSize, ByteRange.All)
      )
    ).unNoneTerminate.flatten

  private def objectName(key: String, n: Int): String = f"$key/chunk_$n%08d"

  private def idFromObjectName(name: String): BinaryId = {
    val idx = name.lastIndexOf('/')
    if (idx > 0) {
      BinaryId(name.substring(0, idx))
    } else {
      BinaryId(name)
    }
  }
}

object MinioChunkedBinaryStore {
  def apply[F[_]: Async](
      config: MinioConfig,
      client: MinioAsyncClient,
      logger: Logger[F]
  ): MinioChunkedBinaryStore[F] =
    new MinioChunkedBinaryStore[F](config, client, logger)

  def apply[F[_]: Async](
      config: MinioConfig,
      logger: Logger[F]
  ): MinioChunkedBinaryStore[F] =
    new MinioChunkedBinaryStore[F](
      config,
      MinioAsyncClient
        .builder()
        .endpoint(config.endpoint)
        .credentials(config.accessKey, config.secretKey)
        .build(),
      logger
    )
}
