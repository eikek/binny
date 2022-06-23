package binny.minio

import java.io.BufferedInputStream

import binny._
import binny.util.{Logger, Stopwatch}
import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.implicits._
import fs2.{Pipe, Stream}
import io.minio.MinioAsyncClient

final class MinioBinaryStore[F[_]: Async](
    val config: MinioConfig,
    private[minio] val client: MinioAsyncClient,
    logger: Logger[F]
) extends BinaryStore[F] {

  private[this] val minio = new Minio[F](client)

  def listIds(prefix: Option[String], chunkSize: Int): Stream[F, BinaryId] = {
    def listBucket(bucket: String) =
      minio
        .listObjects(bucket, None, chunkSize, prefix)
        .map(BinaryId.apply)

    Stream.eval(logger.info(s"List ids with filter: $prefix")) *>
      minio
        .listBuckets()
        .filter(config.keyMapping.bucketFilter)
        .flatMap(listBucket)
  }

  def insert: Pipe[F, Byte, BinaryId] =
    in =>
      Stream
        .eval(BinaryId.random)
        .flatMap(id => in.through(insertWith(id)) ++ Stream.emit(id))

  def insertWith(id: BinaryId): Pipe[F, Byte, Nothing] =
    bytes =>
      Stream.eval {
        val key = config.makeS3Key(id)
        val inStream = bytes.through(fs2.io.toInputStream).map(new BufferedInputStream(_))
        val upload =
          for {
            _ <- Stream.eval(minio.makeBucketIfMissing(key.bucket))
            _ <- minio.uploadObject(key, config.partSize, config.detect, inStream)
          } yield ()

        for {
          _ <- Stopwatch.wrap(d => logger.trace(s"Upload took: $d")) {
            upload.compile.drain
          }
        } yield ()
      }.drain

  def delete(id: BinaryId): F[Unit] = {
    val key = config.makeS3Key(id)
    minio.deleteObject(key)
  }

  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]] = {
    val key = config.makeS3Key(id)
    OptionT(minio.getObjectAsStreamOption(key, config.chunkSize, range))
  }

  def computeAttr(id: BinaryId, hint: Hint) = Kleisli { select =>
    val key = config.makeS3Key(id)
    OptionT(minio.statObject(key).attempt.map(_.toOption)).semiflatMap { stat =>
      select match {
        case AttributeName.ContainsSha256(_) =>
          minio.computeAttr(key, config.detect, hint, config.chunkSize)

        case AttributeName.ContainsLength(_) =>
          val len = stat.size()
          minio
            .detectContentType(key, stat.some, config.detect, hint)
            .map(c => BinaryAttributes(c, len))

        case _ =>
          minio
            .detectContentType(key, stat.some, config.detect, hint)
            .map(c => BinaryAttributes(c, -1L))
      }
    }
  }

  def exists(id: BinaryId) = {
    val key = config.makeS3Key(id)
    minio.statObject(key).attempt.map(_.isRight)
  }
}

object MinioBinaryStore {

  def apply[F[_]: Async](
      config: MinioConfig,
      client: MinioAsyncClient,
      logger: Logger[F]
  ): MinioBinaryStore[F] =
    new MinioBinaryStore[F](config, client, logger)

  def apply[F[_]: Async](
      config: MinioConfig,
      logger: Logger[F]
  ): MinioBinaryStore[F] =
    new MinioBinaryStore[F](
      config,
      MinioAsyncClient
        .builder()
        .endpoint(config.endpoint)
        .credentials(config.accessKey, config.secretKey)
        .build(),
      logger
    )
}
