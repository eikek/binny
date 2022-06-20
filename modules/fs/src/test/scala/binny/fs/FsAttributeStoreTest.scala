package binny.fs

import binny._
import binny.spec.BinaryAttributeStoreSpec
import binny.util.Logger
import cats.effect.IO
import fs2.io.file.{Files, Path}

class FsAttributeStoreTest extends BinaryAttributeStoreSpec[FsAttributeStore[IO]] {
  val logger = Logger.stdout[IO](Logger.Level.Warn, getClass.getSimpleName)

  val attrStore = ResourceSuiteLocalFixture(
    "fs-attr-store",
    for {
      dir <- Files[IO].tempDirectory(Some(Path("target")), "binny-fs-attr-", None)
      store = FsAttributeStore[IO](FsAttrConfig.default(dir))
    } yield store
  )

  override def munitFixtures: Seq[Fixture[_]] = List(attrStore)

  test("calculate sha256 from a file") {
    for {
      ca <- ExampleData.file2M
        .through(BinaryAttributes.compute(ContentTypeDetect.probeFileType, Hint.none))
        .compile
        .lastOrError
      hfs2 <- ExampleData.file2M
        .through(fs2.hash.sha256)
        .through(fs2.text.hex.encode)
        .compile
        .string
      _ = assertEquals(hfs2, ExampleData.file2MAttr.sha256.toHex)
      _ = assertEquals(ca.sha256.toHex, ExampleData.file2MAttr.sha256.toHex)
    } yield ()
  }
}
