package binny.minio

import binny.spec.BinaryStoreSpec
import cats.effect._

class MinioBinaryStoreTest
    extends AbstractMinioTest[MinioBinaryStore[IO]]
    with BinaryStoreSpec[MinioBinaryStore[IO]] {

  java.util.logging.Logger
    .getLogger(classOf[okhttp3.OkHttpClient].getName)
    .setLevel(java.util.logging.Level.FINE)

  val testContext: Fixture[TestContext[MinioBinaryStore[IO]]] =
    ResourceSuiteLocalFixture(
      "minio-store",
      Resource
        .eval(
          TestContext(
            "store-testing",
            (cfg, client) => MinioBinaryStore(cfg, client, logger)
          )
        )
    )

  def binStore: MinioBinaryStore[IO] =
    testContext().store
}
