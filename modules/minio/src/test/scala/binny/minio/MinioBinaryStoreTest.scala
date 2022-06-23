package binny.minio

import binny.spec.BinaryStoreSpec
import cats.effect._

class MinioBinaryStoreTest
    extends AbstractMinioTest[MinioBinaryStore[IO]]
    with BinaryStoreSpec[MinioBinaryStore[IO]] {

  java.util.logging.Logger
    .getLogger(classOf[okhttp3.OkHttpClient].getName)
    .setLevel(java.util.logging.Level.FINE);

  override val containerDef: MinioContainer.Def = new MinioContainer.Def

  // As soon as two test classes use the minio container, things get scary
  override def afterContainersStart(containers: MinioContainer): Unit =
    Thread.sleep(200)

  val testContext: Fixture[TestContext[MinioBinaryStore[IO]]] =
    ResourceSuiteLocalFixture(
      "minio-store",
      Resource
        .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
        .evalMap(cnt =>
          TestContext(
            cnt,
            S3KeyMapping.constant("testing"),
            (cfg, client) => MinioBinaryStore(cfg, client, logger)
          )
        )
    )

  def binStore: MinioBinaryStore[IO] =
    testContext().store
}
