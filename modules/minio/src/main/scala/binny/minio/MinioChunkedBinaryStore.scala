package binny.minio

import binny._
import binny.util.{Logger, RangeCalc}
import cats.data.OptionT
import cats.effect._
import cats.syntax.all._
import fs2.Stream
import io.minio.MinioClient
import scodec.bits.ByteVector

final class MinioChunkedBinaryStore[F[_]: Async](
    val config: MinioConfig,
    private[minio] val client: MinioClient,
    attrStore: BinaryAttributeStore[F],
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
    MinioBinaryStore[F](config.withKeyMapping(keyMapping), client, attrStore, logger)

  def insertChunk(
      id: BinaryId,
      chunkDef: ChunkDef,
      hint: Hint,
      data: ByteVector
  ) =
    InsertChunkResult.validateChunk(chunkDef, config.chunkSize, data.length.toInt) match {
      case Some(bad) => bad.pure[F]
      case None =>
        val ch = chunkDef.fold(identity, _.toTotal(config.chunkSize))
        val s3Key =
          config.keyMapping.makeS3Key(id).changeObjectName(n => objectName(n, ch.index))
        val insert =
          minio.uploadObject(s3Key, config.partSize, config.detect, hint, data)

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
          _ <-
            if (r == InsertChunkResult.Complete)
              attrStore.saveAttr(id, computeAttr(id, hint))
            else ().pure[F]
        } yield r
    }

  private def computeAttr(id: BinaryId, hint: Hint): F[BinaryAttributes] =
    findBinary(id, ByteRange.All)
      .flatMapF(_.through(BinaryAttributes.compute(config.detect, hint)).compile.last)
      .getOrElse(BinaryAttributes.empty)

  def listIds(prefix: Option[String], chunkSize: Int): Stream[F, BinaryId] =
    Stream.eval(logger.info(s"List ids with filter: $prefix")) *>
      minio
        .listBuckets()
        .filter(config.keyMapping.bucketFilter)
        .flatMap(bucket => minio.listObjects(bucket, None, 100, prefix))
        .map(idFromObjectName)

  def insert(hint: Hint) =
    minioStore.insert(hint)

  def insertWith(id: BinaryId, hint: Hint) =
    minioStore.insertWith(id, hint)

  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]] = {
    val list = listChunks(id, max = 2).take(2).compile.toList
    OptionT.liftF(list).flatMap {
      case Nil => OptionT.none[F, Binary[F]]
      case name :: Nil =>
        val key = S3Key(keyMapping.makeS3Key(id).bucket, name)
        OptionT.pure(minio.getObjectAsStream(key, config.chunkSize, range))
      case _ =>
        val allChunks = Stream.evals(
          listChunks(id, 100).compile.toVector.map(_.sorted)
        )
        val offsets = RangeCalc.calcOffset(range, config.chunkSize)
        if (offsets.isNone) {
          val contents =
            allChunks.flatMap(objectName =>
              minio.getObjectAsStream(
                keyMapping.makeS3Key(id).withObjectName(objectName),
                config.chunkSize,
                ByteRange.All
              )
            )
          OptionT.pure(contents)
        } else {
          OptionT.pure(
            allChunks.zipWithIndex
              .drop(offsets.firstChunk)
              .take(offsets.takeChunks)
              .flatMap { case (chunkName, index) =>
                val s3Key = keyMapping.makeS3Key(id).withObjectName(chunkName)
                RangeCalc.chopOffsets(offsets, index.toInt) match {
                  case (Some(start), Some(end)) =>
                    val br = ByteRange.Chunk(start, start + end)
                    minio
                      .getObjectAsStream(s3Key, config.chunkSize, br)

                  case (Some(start), None) =>
                    val br = ByteRange.Chunk(start, Int.MaxValue)
                    minio
                      .getObjectAsStream(s3Key, config.chunkSize, br)

                  case (None, Some(end)) =>
                    val br = ByteRange.Chunk(0, end)
                    minio
                      .getObjectAsStream(s3Key, config.chunkSize, br)

                  case (None, None) =>
                    val br = ByteRange.All
                    minio.getObjectAsStream(s3Key, config.chunkSize, br)
                }
              }
          )
        }
    }
  }

  def exists(id: BinaryId): F[Boolean] = {
    val key = keyMapping.makeS3Key(id)
    minio.statObject(key)
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
      client: MinioClient,
      attrStore: BinaryAttributeStore[F],
      logger: Logger[F]
  ): MinioChunkedBinaryStore[F] =
    new MinioChunkedBinaryStore[F](config, client, attrStore, logger)

  def apply[F[_]: Async](
      config: MinioConfig,
      attrStore: BinaryAttributeStore[F],
      logger: Logger[F]
  ): MinioChunkedBinaryStore[F] =
    new MinioChunkedBinaryStore[F](
      config,
      MinioClient
        .builder()
        .endpoint(config.endpoint)
        .credentials(config.accessKey, config.secretKey)
        .build(),
      attrStore,
      logger
    )
}
