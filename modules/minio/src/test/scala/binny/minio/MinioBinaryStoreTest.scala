package binny.minio

import binny.spec.BinaryStoreSpec
import cats.effect._
import com.dimafeng.testcontainers.munit.TestContainerForAll
import munit.CatsEffectSuite

class MinioBinaryStoreTest
    extends CatsEffectSuite
    with BinaryStoreSpec[MinioBinaryStore[IO]]
    with TestContainerForAll {

  lazy val binStore: Fixture[MinioBinaryStore[IO]] =
    ResourceSuiteLocalFixture(
      "minio-store",
      Resource
        .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
        .map(cnt =>
          Config.store(
            S3KeyMapping.constant("testing"),
            cnt.underlyingUnsafeContainer.getContainerIpAddress,
            cnt.underlyingUnsafeContainer.getMappedPort(9000)
          )
        )
    )

  override val containerDef: MinioContainer.Def = new MinioContainer.Def

  override def munitFixtures: Seq[Fixture[_]] = List(binStore)

}

object MinioBinaryStoreTest {}
