package binny.minio

import java.io.{BufferedInputStream, InputStream}

import binny._
import binny.util.{Logger, Stopwatch}
import cats.data.OptionT
import cats.effect._
import cats.implicits._
import fs2.{Chunk, Pipe, Stream}
import io.minio.MinioClient

final class MinioBinaryStore[F[_]: Async](
    val config: MinioConfig,
    private[minio] val client: MinioClient,
    attrStore: BinaryAttributeStore[F],
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

  def insert(hint: Hint): Pipe[F, Byte, BinaryId] =
    in =>
      Stream
        .eval(BinaryId.random)
        .flatMap(id => in.through(insertWith(id, hint)) ++ Stream.emit(id))

  def insertWith(id: BinaryId, hint: Hint): Pipe[F, Byte, Nothing] =
    bytes =>
      Stream.eval {
        val key = config.makeS3Key(id)
        val inStream = bytes.through(fs2.io.toInputStream).map(new BufferedInputStream(_))
        val upload =
          for {
            _ <- Stream.eval(minio.makeBucketIfMissing(key.bucket))
            _ <- minio.uploadObject(key, config.partSize, config.detect, hint, inStream)
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
    val key = config.makeS3Key(id)
    minio.deleteObject(key)
  }

  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]] = {
    val key = config.makeS3Key(id)
    OptionT(minio.statObject(key).map {
      case true  => Some(dataStream(key, range))
      case false => None
    })
  }

  def exists(id: BinaryId) = {
    val key = config.makeS3Key(id)
    minio.statObject(key)
  }

  def findAttr(id: BinaryId): OptionT[F, BinaryAttributes] =
    attrStore.findAttr(id)

  private def dataStream(key: S3Key, range: ByteRange): Stream[F, Byte] = {
    val fin = minio.getObject(key, range).map(a => a: InputStream)
    fs2.io
      .unsafeReadInputStream(fin, config.chunkSize, closeAfterUse = true)
      .mapChunks(c => Chunk.byteVector(c.toByteVector))
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
