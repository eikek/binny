package binny.minio

import binny.BinaryAttributeStore
import binny.spec.BinaryStoreSpec
import binny.util.Logger
import cats.effect._
import com.dimafeng.testcontainers.munit.TestContainerForAll

class MinioBinaryStoreTest
    extends BinaryStoreSpec[MinioBinaryStore[IO]]
    with TestContainerForAll {

  val logger = Logger.stdout[IO](Logger.Level.Off, getClass.getSimpleName)
  val binStore: Fixture[MinioBinaryStore[IO]] =
    ResourceSuiteLocalFixture(
      "minio-store",
      Resource
        .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
        .map(cnt =>
          MinioBinaryStore(
            cnt.createConfig(S3KeyMapping.constant("testing")),
            BinaryAttributeStore.empty[IO],
            logger
          )
        )
    )

  override val containerDef: MinioContainer.Def = new MinioContainer.Def

  override def munitFixtures: Seq[Fixture[_]] = List(binStore)

}

object MinioBinaryStoreTest {}
