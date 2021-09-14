package binny.minio

import binny.spec.BinaryStoreSpec
import cats.effect._
import com.dimafeng.testcontainers.munit.TestContainerForAll

class MinioBinaryStoreTest
    extends BinaryStoreSpec[MinioBinaryStore[IO]]
    with TestContainerForAll {

  lazy val binStore: SyncIO[FunFixture[MinioBinaryStore[IO]]] =
    ResourceFixture(
      Resource
        .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
        .map(cnt =>
          Config.store(
            S3KeyMapping.constant("testing"),
            cnt.underlyingUnsafeContainer.getContainerIpAddress
          )
        )
    )

  override val containerDef: MinioContainer.Def.type = MinioContainer.Def

//  override def munitIgnore =
//    !MinioBinaryStoreTest.minioPresent

}

object MinioBinaryStoreTest {

}
