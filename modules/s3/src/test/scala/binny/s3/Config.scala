package binny.s3

import java.time.Duration

import binny.{BinaryAttributeStore, Log4sLogger}
import cats.effect._
import io.minio.MinioClient
import okhttp3.OkHttpClient

object Config {
  def testing(m: S3KeyMapping): S3Config =
    S3Config
      .default(
        "http://172.17.0.21:9000",
        "root",
        "d2Fscm/f",
        m
      )
      .copy(chunkSize = 100 * 1024)

  def store(m: S3KeyMapping): S3BinaryStore[IO] =
    S3BinaryStore(
      testing(m),
      BinaryAttributeStore.empty[IO],
      Log4sLogger(org.log4s.getLogger)
    )

  def instancePresent(cfg: S3Config): IO[Boolean] = {
    val minio = new Minio[IO](
      MinioClient
        .builder()
        .httpClient(
          new OkHttpClient.Builder().connectTimeout(Duration.ofMillis(300)).build()
        )
        .endpoint(cfg.endpoint)
        .credentials(cfg.accessKey, cfg.secretKey)
        .build()
    )
    minio.makeBucketIfMissing("testbucket").attempt.map(_.isRight)
  }
}
