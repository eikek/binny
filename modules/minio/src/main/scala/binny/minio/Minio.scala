package binny.minio

import java.io.InputStream
import java.security.MessageDigest
import java.util.concurrent.{CompletableFuture, CompletionException}

import scala.jdk.CollectionConverters._
import scala.util.Using

import binny._
import cats.effect._
import cats.implicits._
import fs2.{Chunk, Stream}
import io.minio._
import io.minio.errors.ErrorResponseException
import scodec.bits.ByteVector

final private[minio] class Minio[F[_]: Async](client: MinioAsyncClient) {
  val async = Async[F]

  private def future[A](f: => CompletableFuture[A]): F[A] =
    async.fromCompletableFuture(Async[F].delay(f))

  def listBuckets(): Stream[F, String] =
    Stream
      .eval(future(client.listBuckets()))
      .flatMap(jl => Stream.chunk(Chunk.iterable(jl.asScala)))
      .map(_.name())

  def listObjects(
      bucket: String,
      startAfter: Option[String],
      maxKeys: Int,
      prefix: Option[String]
  ): Stream[F, String] = {
    val chunk =
      Sync[F].blocking {
        val args = ListObjectsArgs
          .builder()
          .recursive(true)
          .bucket(bucket)
          .maxKeys(maxKeys)

        startAfter.foreach(args.startAfter)
        prefix.foreach(args.prefix)

        val result = client.listObjects(args.build())
        val ch = Chunk.iterable(result.asScala.map(_.get.objectName))
        (
          if (ch.isEmpty) Stream.empty else Stream.chunk(ch),
          ch.last.filter(_ => ch.size == maxKeys)
        )
      }

    Stream.eval(chunk).flatMap { case (ch, last) =>
      last match {
        case Some(el) =>
          ch ++ listObjects(bucket, Some(el), maxKeys, prefix)
        case None =>
          ch
      }
    }
  }

  private def bucketExists(name: String): F[Boolean] = {
    val args = BucketExistsArgs
      .builder()
      .bucket(name)
      .build()
    future(client.bucketExists(args)).map(_.booleanValue())
  }

  private def makeBucket(name: String): F[Unit] = {
    val args = MakeBucketArgs
      .builder()
      .bucket(name)
      .build()
    future(client.makeBucket(args)).void
  }

  def makeBucketIfMissing(name: String): F[Unit] =
    bucketExists(name).flatMap {
      case true => ().pure[F]
      case false =>
        makeBucket(name).attempt.flatMap {
          case Right(n) => n.pure[F]
          case Left(ex) =>
            // check if another request created the bucket in the meantime, if not throw
            bucketExists(name).flatMap {
              case true  => ().pure[F]
              case false => Sync[F].raiseError(ex)
            }
        }
    }

  def uploadObject(
      key: S3Key,
      partSize: Int,
      detect: ContentTypeDetect,
      in: Stream[F, InputStream]
  ): Stream[F, Unit] =
    in.evalMap(javaStream =>
      for {
        ct <- Sync[F].blocking {
          if (javaStream.markSupported()) {
            val buffer = new Array[Byte](32)
            javaStream.mark(65)
            val read = javaStream.read(buffer)
            val ret = detect.detect(ByteVector.view(buffer, 0, read), Hint.none)
            javaStream.reset()
            ret
          } else SimpleContentType.octetStream
        }
        args = new PutObjectArgs.Builder()
          .bucket(key.bucket)
          .`object`(key.objectName)
          .contentType(ct.contentType)
          .stream(javaStream, -1, partSize)
          .build()

        _ <- future(client.putObject(args))
      } yield ()
    )

  def uploadObject(
      key: S3Key,
      partSize: Int,
      detect: ContentTypeDetect,
      in: ByteVector
  ): F[ObjectWriteResponse] = {
    val ct = detect.detect(in, Hint.none)
    val args = new PutObjectArgs.Builder()
      .bucket(key.bucket)
      .`object`(key.objectName)
      .contentType(ct.contentType)
      .stream(in.toInputStream, in.length, partSize)
      .build()

    future(client.putObject(args))
  }

  def deleteObject(key: S3Key): F[Unit] = {
    val args = RemoveObjectArgs
      .builder()
      .bucket(key.bucket)
      .`object`(key.objectName)
      .build()
    future(client.removeObject(args)).void
  }

  def deleteBucket(name: String): F[Unit] = {
    val args = RemoveBucketArgs
      .builder()
      .bucket(name)
      .build()
    future(client.removeBucket(args)).void
  }

  def statObject(key: S3Key): F[StatObjectResponse] = {
    val args = StatObjectArgs
      .builder()
      .bucket(key.bucket)
      .`object`(key.objectName)
      .build()
    future(client.statObject(args))
  }

  def exists(key: S3Key): F[Boolean] =
    statObject(key).redeemWith(decodeNotFoundAs(false), _ => true.pure[F])

  def getObject(key: S3Key, range: ByteRange): F[GetObjectResponse] = {
    val aargs = GetObjectArgs
      .builder()
      .bucket(key.bucket)
      .`object`(key.objectName)

    val args = range match {
      case ByteRange.All => aargs.build()
      case ByteRange.Chunk(offset, length) =>
        val ao = if (offset > 0) aargs.offset(offset) else aargs
        val lo =
          if (length >= 0 && length < Int.MaxValue) ao.length(length.toLong) else ao
        lo.build()
    }
    future(client.getObject(args))
  }

  def getObjectOption(
      key: S3Key,
      range: ByteRange
  ): F[Option[GetObjectResponse]] =
    getObject(key, range).redeemWith(
      decodeNotFoundAs(Option.empty[GetObjectResponse]),
      _.some.pure[F]
    )

  def isNotFound(ex: Throwable): Boolean =
    ex match {
      case e: ErrorResponseException if e.response().code() == 404 => true
      case e: CompletionException => isNotFound(e.getCause)
      case _                      => false
    }

  def decodeNotFoundAs[A](defaultValue: => A)(ex: Throwable): F[A] =
    if (isNotFound(ex)) Sync[F].pure(defaultValue)
    else Sync[F].raiseError(ex)

  def getObjectAsStream(key: S3Key, chunkSize: Int, range: ByteRange): Binary[F] = {
    val input = getObject(key, range).map(a => a: InputStream)
    fs2.io
      .unsafeReadInputStream(input, chunkSize, closeAfterUse = true)
      .mapChunks(c => Chunk.byteVector(c.toByteVector))
  }

  // Note: this method is not safe to use when the resulting stream is unused!
  // the stream must be pulled in order to close the response
  def getObjectAsStreamOption(
      key: S3Key,
      chunkSize: Int,
      range: ByteRange
  ): F[Option[Binary[F]]] =
    getObject(key, range)
      .redeemWith(decodeNotFoundAs(Option.empty[GetObjectResponse]), _.some.pure[F])
      .map(_.map { resp =>
        fs2.io
          .unsafeReadInputStream(
            (resp: InputStream).pure[F],
            chunkSize,
            closeAfterUse = true
          )
      })

  def computeAttr(
      key: S3Key,
      detect: ContentTypeDetect,
      hint: Hint,
      chunkSize: Int
  ): F[BinaryAttributes] = {
    val args = GetObjectArgs
      .builder()
      .bucket(key.bucket)
      .`object`(key.objectName)
      .build()

    future(client.getObject(args)).flatMap(resp =>
      Sync[F].blocking {
        val md = MessageDigest.getInstance("SHA-256")
        var len = 0L
        var ct = None: Option[SimpleContentType]
        val buf = new Array[Byte](chunkSize)
        var read = -1
        Using.resource(resp) { rr =>
          while ({
            read = rr.read(buf)
            read
          } > 0) {
            md.update(buf, 0, read)
            len = len + read
            if (ct.isEmpty) {
              ct = Some(detect.detect(ByteVector.view(buf, 0, read), hint))
            }
          }
        }
        BinaryAttributes(
          ByteVector.view(md.digest()),
          ct.getOrElse(SimpleContentType.octetStream),
          len
        )
      }
    )
  }

  def detectContentType(
      key: S3Key,
      stat: Option[StatObjectResponse],
      detect: ContentTypeDetect,
      hint: Hint
  ): F[SimpleContentType] = {
    val readCt =
      getObjectAsStream(key, 50, ByteRange.Chunk(0, 50))
        .through(detect.detectStream(hint))
        .compile
        .lastOrError
    stat
      .flatMap(s => Option(s.contentType()))
      .map(SimpleContentType(_))
      .filter(c => !c.isOctetStream)
      .map(_.pure[F])
      .getOrElse(readCt)
  }
}
