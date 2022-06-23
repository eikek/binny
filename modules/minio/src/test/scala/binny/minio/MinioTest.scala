package binny.minio

import binny.ByteRange
import cats.effect._
import com.dimafeng.testcontainers.munit.TestContainerForAll
import io.minio.MinioAsyncClient
import munit.CatsEffectSuite

class MinioTest extends CatsEffectSuite with TestContainerForAll {

  override val containerDef: MinioContainer.Def = new MinioContainer.Def

  // As soon as two test classes use the minio container, things get scary
  override def afterContainersStart(containers: MinioContainer): Unit =
    Thread.sleep(200)

  test("not found on get object") {
    withContainers { cnt =>
      val client = MinioAsyncClient
        .builder()
        .endpoint(cnt.endpoint)
        .credentials(cnt.accessKey, cnt.secretKey)
        .build()
      val minio = new Minio[IO](client)

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
}
