package binny.minio

import java.time.Duration

import binny.{BinaryAttributeStore, Log4sLogger}
import cats.effect._
import io.minio.MinioClient
import okhttp3.OkHttpClient

object Config {
  def testing(m: S3KeyMapping): MinioConfig =
    MinioConfig
      .default(
        "http://172.17.0.2:9000",
        "root",
        "d2Fscm/f",
        m
      )
      .copy(chunkSize = 100 * 1024)

  def store(m: S3KeyMapping): MinioBinaryStore[IO] =
    MinioBinaryStore(
      testing(m),
      BinaryAttributeStore.empty[IO],
      Log4sLogger(org.log4s.getLogger)
    )

  def instancePresent(
      endpoint: String,
      accessKey: String,
      secretKey: String
  ): IO[Boolean] = {
    val minio = new Minio[IO](
      MinioClient
        .builder()
        .httpClient(
          new OkHttpClient.Builder().connectTimeout(Duration.ofMillis(300)).build()
        )
        .endpoint(endpoint)
        .credentials(accessKey, secretKey)
        .build()
    )
    minio.makeBucketIfMissing("testbucket").attempt.map(_.isRight)
  }

  def instancePresent(cfg: MinioConfig): IO[Boolean] =
    instancePresent(cfg.endpoint, cfg.accessKey, cfg.secretKey)
}
