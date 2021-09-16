package binny.minio

import java.time.Duration

import binny.{BinaryAttributeStore, Log4sLogger}
import cats.effect._
import io.minio.MinioClient
import okhttp3.OkHttpClient

object MinioTestCfg {
  def testing(m: S3KeyMapping, ip: String, port: Int): MinioConfig =
    MinioConfig
      .default(
        s"http://$ip:$port",
        "root",
        "d2Fscm/f",
        m
      )
      .copy(chunkSize = 100 * 1024)

  def store(m: S3KeyMapping, ip: String, port: Int): MinioBinaryStore[IO] =
    MinioBinaryStore(
      testing(m, ip, port),
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
