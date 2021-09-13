package binny.minio

import binny._
import munit.CatsEffectSuite

class MinioBinaryStoreTest extends CatsEffectSuite with BinaryStoreAsserts {

  val store = MinioBinaryStoreTest.store

  override def munitIgnore =
    !MinioBinaryStoreTest.minioPresent

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

object MinioBinaryStoreTest {
  import cats.effect.unsafe.implicits._

  private val store = Config.store(S3KeyMapping.constant("testing"))
  private val minioPresent = Config.instancePresent(store.config).unsafeRunSync()
}
