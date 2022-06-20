package binny.minio

import binny._
import binny.spec.ChunkedBinaryStoreSpec
import binny.util.Logger
import cats.effect._
import com.dimafeng.testcontainers.munit.TestContainerForAll
import io.minio.GetObjectArgs

class MinioChunkedBinaryStoreTest
    extends ChunkedBinaryStoreSpec[MinioChunkedBinaryStore[IO]]
    with TestContainerForAll {

  override val containerDef: MinioContainer.Def = new MinioContainer.Def

  val logger = Logger.stdout[IO](Logger.Level.Off, getClass.getSimpleName)
  val binStore: Fixture[MinioChunkedBinaryStore[IO]] =
    ResourceSuiteLocalFixture(
      "minio-store",
      Resource
        .make(IO(containerDef.start()))(cnt => IO(cnt.stop()))
        .map(cnt =>
          MinioChunkedBinaryStore(
            cnt.createConfig(S3KeyMapping.constant("testing")),
            BinaryAttributeStore.empty[IO],
            logger
          )
        )
    )

  override def munitFixtures: Seq[Fixture[_]] = List(binStore)

  test("uploading file with content-type") {
    val hint = Hint("logo.png", "image/png")
    val detect = ContentTypeDetect.probeFileType
    val ctype =
      ExampleData.logoPng.through(detect.detectStream[IO](hint)).compile.lastOrError
    assertIO(ctype, SimpleContentType("image/png"))

    val fs = binStore()
    for {
      binId <- ExampleData.logoPng.through(fs.insert(hint)).compile.lastOrError
      getResp = fs.client.getObject {
        val go = new GetObjectArgs.Builder()
        val s3key = fs.keyMapping.makeS3Key(binId)
        go.bucket(s3key.bucket)
        go.`object`(s3key.objectName)
        go.build()
      }
      ct = getResp.headers().get("Content-Type")
      _ = assertEquals(ct, "image/png")
    } yield ()
  }

  test("listing files with folder structure") {
    val fs = binStore()
    val binId = BinaryId("folderA/folderB/test.png")
    for {
      _ <- ExampleData.logoPng.through(fs.insertWith(binId, Hint.none)).compile.drain
      all <- fs.listIds(Some("folder"), 20).compile.toVector
      _ = assertEquals(all, Vector(binId))
    } yield ()
  }
}
