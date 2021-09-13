package binny.minio

import java.io.InputStream
import java.security.MessageDigest

import binny.ContentTypeDetect.Hint
import binny._
import cats.effect._
import cats.implicits._
import fs2.Stream
import io.minio._
import scodec.bits.ByteVector

final private[minio] class Minio[F[_]: Sync](client: MinioClient) {

  def bucketExists(name: String): F[Boolean] =
    Sync[F].blocking {
      val args = BucketExistsArgs
        .builder()
        .bucket(name)
        .build()
      client.bucketExists(args)
    }

  def makeBucket(name: String): F[Unit] = {
    val args = MakeBucketArgs
      .builder()
      .bucket(name)
      .build()
    Sync[F].blocking(client.makeBucket(args))
  }

  def makeBucketIfMissing(name: String): F[Unit] =
    bucketExists(name).flatMap {
      case true  => ().pure[F]
      case false => makeBucket(name)
    }

  def uploadObject(
      key: S3Key,
      partSize: Int,
      in: Stream[F, InputStream]
  ): Stream[F, Unit] =
    in.evalMap(javaStream =>
      Sync[F].blocking {
        val args = new PutObjectArgs.Builder()
          .bucket(key.bucket)
          .`object`(key.objectName)
          .stream(javaStream, -1, partSize)
          .build()

        client.putObject(args)
        ()
      }
    )

  def deleteObject(key: S3Key): F[Unit] = {
    val args = RemoveObjectArgs
      .builder()
      .bucket(key.bucket)
      .`object`(key.objectName)
      .build()
    Sync[F].blocking(client.removeObject(args))
  }

  def statObject(key: S3Key): F[Boolean] = {
    val args = StatObjectArgs
      .builder()
      .bucket(key.bucket)
      .`object`(key.objectName)
      .build()
    Sync[F].blocking(client.statObject(args)).attempt.map(_.isRight)
  }

  def getObject(key: S3Key, range: ByteRange): F[InputStream] = {
    val aargs = GetObjectArgs
      .builder()
      .bucket(key.bucket)
      .`object`(key.objectName)

    val args = range match {
      case ByteRange.All => aargs.build()
      case ByteRange.Chunk(offset, length) =>
        aargs.offset(offset).length(length).build()
    }
    Sync[F].blocking(client.getObject(args))
  }

  def computeAttr(
      key: S3Key,
      detect: ContentTypeDetect,
      hint: Hint,
      chunkSize: Int
  ): F[BinaryAttributes] =
    Sync[F].blocking {
      val args = GetObjectArgs
        .builder()
        .bucket(key.bucket)
        .`object`(key.objectName)
        .build()

      val md = MessageDigest.getInstance("SHA-256")
      var len = 0L
      var ct = (None: Option[SimpleContentType])
      val buf = new Array[Byte](chunkSize)

      var read = -1
      val in = client.getObject(args)
      while ({ read = in.read(buf); read } > 0) {
        md.update(buf, 0, read)
        len = len + read
        if (ct.isEmpty) {
          ct = Some(detect.detect(ByteVector.view(buf, 0, read), hint))
        }
      }
      BinaryAttributes(
        ByteVector.view(md.digest()),
        ct.getOrElse(SimpleContentType.octetStream),
        len
      )
    }
}
