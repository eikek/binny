package binny.minio

import binny._
import binny.util.Logger
import cats.effect._
import com.dimafeng.testcontainers.munit.TestContainerForAll
import io.minio.GetObjectArgs
import munit.CatsEffectSuite

abstract class AbstractMinioTest[S <: BinaryStore[IO]]
    extends CatsEffectSuite
    with TestContainerForAll {

  override val containerDef: MinioContainer.Def = new MinioContainer.Def

  // As soon as two test classes use the minio container, things get scary
  override def afterContainersStart(containers: MinioContainer): Unit =
    Thread.sleep(200)

  val logger = Logger.stdout[IO](Logger.Level.Off, getClass.getSimpleName)

  val testContext: Fixture[TestContext[S]]

  override def munitFixtures = super.munitFixtures ++ Seq(testContext)

  test("uploading file with content-type") {
    val hint = Hint("logo.png", "image/png")
    val detect = ContentTypeDetect.probeFileType
    val ctype =
      ExampleData.logoPng.through(detect.detectStream[IO](hint)).compile.lastOrError
    assertIO(ctype, SimpleContentType("image/png"))

    val testCtx = testContext().changeConfig(
      _.withContentTypeDetect(
        ContentTypeDetect.constant(SimpleContentType("image/png"))
      )
    )
    val fs = testCtx.store
    val client = testCtx.client
    for {
      binId <- ExampleData.logoPng.through(fs.insert).compile.lastOrError
      getResp <- IO.fromCompletableFuture(IO(client.getObject {
        val go = new GetObjectArgs.Builder()
        val s3key = testCtx.makeS3Key(binId)
        go.bucket(s3key.bucket)
        go.`object`(s3key.objectName)
        go.build()
      }))
      ct = getResp.headers().get("Content-Type")
      _ = assertEquals(ct, "image/png")
    } yield ()
  }

  test("listing files with folder structure") {
    val fs = testContext().store
    val binId = BinaryId("folderA/folderB/test.png")
    for {
      _ <- ExampleData.logoPng.through(fs.insertWith(binId)).compile.drain
      all <- fs.listIds(Some("folder"), 20).compile.toVector
      _ = assertEquals(all, Vector(binId))
    } yield ()
  }
}
