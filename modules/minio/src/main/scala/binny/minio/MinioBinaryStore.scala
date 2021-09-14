package binny.minio

import binny._
import binny.util.{Logger, Stopwatch}
import cats.data.OptionT
import cats.effect._
import cats.implicits._
import fs2.{Pipe, Stream}
import io.minio.MinioClient

final class MinioBinaryStore[F[_]: Async](
    val config: MinioConfig,
    client: MinioClient,
    attrStore: BinaryAttributeStore[F],
    logger: Logger[F]
) extends BinaryStore[F] {

  private[this] val minio = new Minio[F](client)

  def insert(hint: ContentTypeDetect.Hint): Pipe[F, Byte, BinaryId] =
    in =>
      Stream
        .eval(BinaryId.random)
        .flatMap(id => in.through(insertWith(id, hint)) ++ Stream.emit(id))

  def insertWith(id: BinaryId, hint: ContentTypeDetect.Hint): Pipe[F, Byte, Nothing] =
    bytes =>
      Stream.eval {
        val key = config.keyMapping(id)
        val inStream = bytes.through(fs2.io.toInputStream)
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
              id,
              minio.computeAttr(key, config.detect, hint, config.chunkSize)
            )
          }
        } yield ()
      }.drain

  def delete(id: BinaryId): F[Unit] = {
    val key = config.keyMapping(id)
    minio.deleteObject(key)
  }

  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]] = {
    val key = config.keyMapping(id)
    OptionT(minio.statObject(key).map {
      case true  => Some(dataStream(key, range))
      case false => None
    })
  }

  def findAttr(id: BinaryId): OptionT[F, BinaryAttributes] =
    attrStore.findAttr(id)

  private def dataStream(key: S3Key, range: ByteRange): Stream[F, Byte] = {
    val fin = minio.getObject(key, range)
    fs2.io.readInputStream(fin, config.chunkSize, closeAfterUse = true)
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
