package binny.minio

import binny.spec.ChunkedBinaryStoreSpec
import cats.effect._

class MinioChunkedBinaryStoreTest
    extends AbstractMinioTest[MinioChunkedBinaryStore[IO]]
    with ChunkedBinaryStoreSpec[MinioChunkedBinaryStore[IO]] {

  val testContext: Fixture[TestContext[MinioChunkedBinaryStore[IO]]] =
    ResourceSuiteLocalFixture(
      "minio-store",
      Resource
        .eval(
          TestContext(
            "chunked-store",
            (cfg, client) => MinioChunkedBinaryStore(cfg, client, logger)
          )
        )
        .map(ctx => ctx.copy(createS3Key = Some(ctx.store.keyMapping.makeS3Key)))
    )

  override def binStore = testContext().store
}
