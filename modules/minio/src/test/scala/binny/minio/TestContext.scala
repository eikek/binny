package binny.minio

import binny.{BinaryId, BinaryStore}
import cats.effect._
import io.minio.MinioAsyncClient

case class TestContext[S <: BinaryStore[IO]](
    client: MinioAsyncClient,
    config: MinioConfig,
    private val create: (MinioConfig, MinioAsyncClient) => S,
    private val createS3Key: Option[BinaryId => S3Key] = None
) {

  lazy val store: S = create(config, client)

  def changeConfig(f: MinioConfig => MinioConfig): TestContext[S] =
    copy(config = f(config))

  def makeS3Key(id: BinaryId): S3Key =
    createS3Key.getOrElse(config.makeS3Key _).apply(id)
}

object TestContext {

  def apply[S <: BinaryStore[IO]](
      bucket: String,
      create: (MinioConfig, MinioAsyncClient) => S
  ): IO[TestContext[S]] = IO {
    val client = MinioTestCfg.client
    val cfg = MinioTestCfg.default(bucket)
    TestContext(client, cfg, create)
  }
}
