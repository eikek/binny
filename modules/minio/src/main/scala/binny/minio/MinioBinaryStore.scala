package binny.minio

import binny._
import binny.util.{Logger, Stopwatch}
import cats.data.OptionT
import cats.effect._
import cats.implicits._
import fs2.Stream
import io.minio.MinioClient

final class MinioBinaryStore[F[_]: Async](
    val config: MinioConfig,
    client: MinioClient,
    attrStore: BinaryAttributeStore[F],
    logger: Logger[F]
) extends BinaryStore[F] {

  private val minio = new Minio[F](client)

  def insertWith(data: BinaryData[F], hint: ContentTypeDetect.Hint): F[Unit] = {
    val key = config.keyMapping(data.id)
    val inStream = data.bytes.through(fs2.io.toInputStream)
    val upload =
      for {
        _ <- Stream.eval(minio.makeBucketIfMissing(key.bucket))
        _ <- minio.uploadObject(key, config.partSize, inStream)
      } yield ()

    for {
      _ <- Stopwatch.wrap(d => logger.trace(s"Upload took: $d")) {
        upload.compile.drain
      }
      _ <- Stopwatch.wrap(d =>
        logger.trace(s"Computing and storing attributes took: $d")
      ) {
        attrStore.saveAttr(
          data.id,
          minio.computeAttr(key, config.detect, hint, config.chunkSize)
        )
      }
    } yield ()
  }

  def delete(id: BinaryId): F[Boolean] = {
    val key = config.keyMapping(id)
    for {
      exists <- minio.statObject(key)
      _ <- if (exists) minio.deleteObject(key) else ().pure[F]
    } yield exists
  }

  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, BinaryData[F]] = {
    val key = config.keyMapping(id)
    OptionT(minio.statObject(key).map {
      case true  => Some(BinaryData(id, dataStream(key, range)))
      case false => None
    })
  }

  def findAttr(id: BinaryId): OptionT[F, BinaryAttributes] =
    attrStore.findAttr(id)

  private def dataStream(key: S3Key, range: ByteRange): Stream[F, Byte] = {
    val fin = minio.getObject(key, range)
    fs2.io.readInputStream(fin, config.chunkSize, true)
  }
}

object MinioBinaryStore {

  def apply[F[_]: Async](
      config: MinioConfig,
      client: MinioClient,
      attrStore: BinaryAttributeStore[F],
      logger: Logger[F]
  ): MinioBinaryStore[F] =
    new MinioBinaryStore[F](config, client, attrStore, logger)

  def apply[F[_]: Async](
      config: MinioConfig,
      attrStore: BinaryAttributeStore[F],
      logger: Logger[F]
  ): MinioBinaryStore[F] =
    new MinioBinaryStore[F](
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
