package binny.s3

import java.net.URI

import binny._
import cats.data.OptionT
import cats.effect._
import cats.implicits._
import fs2.aws.s3.S3
import io.laserdisc.pure.s3.tagless.Interpreter
import software.amazon.awssdk.auth.credentials.{AwsCredentials, StaticCredentialsProvider}
import software.amazon.awssdk.services.s3.{S3AsyncClient, S3AsyncClientBuilder}

final class S3BinaryStore[F[_]: Async](
    s3: S3[F],
    attrStore: BinaryAttributeStore[F],
    val config: S3Config
) extends BinaryStore[F] {

  def insertWith(data: BinaryData[F], hint: ContentTypeDetect.Hint): F[Unit] = {
    val key = config.mapping(data.id)
    val uploadPipe =
      s3.uploadFileMultipart(key.bucket, key.key, config.partSize).andThen(_.drain)

    val attr = data.bytes
      .observe(uploadPipe)
      .through(BinaryAttributes.compute(config.detect, hint))
      .compile
      .lastOrError

    attrStore.saveAttr(data.id, attr) *> attr.map(_ => ())
  }

  def delete(id: BinaryId): F[Boolean] = {
    val key = config.mapping(id)
    s3.delete(key.bucket, key.key).map(_ => true)
  }

  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, BinaryData[F]] = {
    val key = config.mapping(id)
    val bytes = s3.readFileMultipart(key.bucket, key.key, config.partSize)
    OptionT(bytes.head.compile.last)
      .map(_ => BinaryData(id, bytes))
  }

  def findAttr(id: BinaryId): OptionT[F, BinaryAttributes] =
    attrStore.findAttr(id)
}

object S3BinaryStore {

  def apply[F[_]: Async](
      s3Client: S3AsyncClientBuilder,
      attrStore: BinaryAttributeStore[F],
      config: S3Config
  ): Resource[F, S3BinaryStore[F]] =
    for {
      s3inter <- Interpreter[F].S3AsyncClientOpResource(s3Client)
      s3 <- Resource.eval(S3.create(s3inter))
    } yield new S3BinaryStore[F](s3, attrStore, config)

  def basic[F[_]: Async](
      creds: AwsCredentials,
      endpoint: URI,
      attrStore: BinaryAttributeStore[F],
      config: S3Config
  ): Resource[F, S3BinaryStore[F]] =
    apply(
      S3AsyncClient
        .builder()
        .credentialsProvider(StaticCredentialsProvider.create(creds))
        .endpointOverride(endpoint),
      attrStore,
      config
    )
}
