package binny.s3

import java.net.URI

import binny._
import binny.minio.Config
import cats.effect._
import cats.implicits._
import eu.timepit.refined.types.string.NonEmptyString
import fs2.aws.s3.{BucketName, S3}
import io.laserdisc.pure.s3.tagless.Interpreter
import munit.CatsEffectSuite
import software.amazon.awssdk.auth.credentials.{
  AwsBasicCredentials,
  StaticCredentialsProvider
}
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient

final class S3BinaryStoreTest extends CatsEffectSuite with BinaryStoreAsserts {
  lazy val store = S3BinaryStoreTest.store

  override def munitIgnore =
    !S3BinaryStoreTest.minioPresent

  test("insert and load") {
    store.assertInsertAndLoad(ExampleData.helloWorld)
    store.assertInsertAndLoad(ExampleData.empty)
    store.assertInsertAndLoadLargerFile()
  }

  test("insert and load range") {
    for {
      data <- store.insertAndLoadRange(ExampleData.helloWorld, ByteRange(2, 5))
      str <- data.bytes.through(fs2.text.utf8.decode).foldMonoid.compile.lastOrError
      _ = assertEquals(str, "llo W")
    } yield ()
  }

  test("insert and delete") {
    store.assertInsertAndDelete()
  }
}

object S3BinaryStoreTest {
  import cats.effect.unsafe.implicits._

  private def store: S3BinaryStore[IO] =
    create(
      BinaryAttributeStore.empty[IO],
      S3Config.default(BucketName(NonEmptyString.unsafeFrom("testing")))
    ).unsafeRunSync()

  private val minioPresent = Config
    .instancePresent(
      Config.testing(binny.minio.S3KeyMapping.constant("testing"))
    )
    .unsafeRunSync()

  def create(
      attrStore: BinaryAttributeStore[IO],
      config: S3Config
  ): IO[S3BinaryStore[IO]] = {
    val mcfg =
      Config.testing(binny.minio.S3KeyMapping.constant("testing"))
    val s3Client = S3AsyncClient
      .builder()
      .endpointOverride(URI.create(mcfg.endpoint))
      .credentialsProvider(
        StaticCredentialsProvider.create(
          AwsBasicCredentials.create(mcfg.accessKey, mcfg.secretKey)
        )
      )
      .region(Region.EU_WEST_1)
      .build()

    for {
      s3 <- S3.create[IO](Interpreter[IO].create(s3Client))
    } yield new S3BinaryStore[IO](s3, attrStore, config)
  }

}
