package binny.minio

import binny.ByteRange
import cats.effect._
import munit.CatsEffectSuite

class MinioTest extends CatsEffectSuite {

  test("not found on get object") {
    val minio = new Minio[IO](MinioTestCfg.client)

    for {
      resp1 <- minio.getObjectOption(S3Key("test-bucket", "bla"), ByteRange.All)
      _ <- minio.makeBucketIfMissing("test-bucket")
      resp2 <- minio.getObjectOption(S3Key("test-bucket", "b"), ByteRange.All)
    } yield {
      assertEquals(resp1, None)
      assertEquals(resp2, None)
    }
  }
}
