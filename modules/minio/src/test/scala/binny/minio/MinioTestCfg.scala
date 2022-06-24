package binny.minio

import io.minio.MinioAsyncClient

object MinioTestCfg {

  val testEndpoint = sys.env.getOrElse("BINNY_MINIO_CI_ENDPOINT", "http://localhost:9182")
  val testAccessKey = sys.env.getOrElse("BINNY_MINIO_CI_ACCESS_KEY", "binny")
  val testSecretKey = sys.env.getOrElse("BINNY_MINIO_CI_SECRET_KEY", "binnybinny")

  def default(bucket: String): MinioConfig =
    MinioConfig.default(
      testEndpoint,
      testAccessKey,
      testSecretKey,
      S3KeyMapping.constant(bucket)
    )

  def client: MinioAsyncClient =
    MinioAsyncClient
      .builder()
      .endpoint(testEndpoint)
      .credentials(testAccessKey, testSecretKey)
      .build()
}
