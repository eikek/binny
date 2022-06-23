package binny.minio

import binny.spec.ChunkedBinaryStoreSpec
import cats.effect._

class MinioChunkedBinaryStoreTest
    extends AbstractMinioTest[MinioChunkedBinaryStore[IO]]
    with ChunkedBinaryStoreSpec[MinioChunkedBinaryStore[IO]] {

  override val containerDef: MinioContainer.Def = new MinioContainer.Def

  // As soon as two test classes use the minio container, things get scary
  override def afterContainersStart(containers: MinioContainer): Unit =
    Thread.sleep(200)

  val testContext: Fixture[TestContext[MinioChunkedBinaryStore[IO]]] =
    ResourceSuiteLocalFixture(
      "minio-store",
      Resource
        .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
        .evalMap(cnt =>
          TestContext(
            cnt,
            S3KeyMapping.constant("testing"),
            (cfg, client) => MinioChunkedBinaryStore(cfg, client, logger)
          )
        )
        .map(ctx => ctx.copy(createS3Key = Some(ctx.store.keyMapping.makeS3Key)))
    )

  override def binStore = testContext().store
}
