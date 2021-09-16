package binny.fs

import binny.spec.BinaryAttributeStoreSpec
import cats.effect.IO
import fs2.io.file.{Files, Path}

class FsAttributeStoreTest extends BinaryAttributeStoreSpec[FsAttributeStore[IO]] {

  val attrStore = ResourceSuiteLocalFixture(
    "fs-attr-store",
    for {
      dir <- Files[IO].tempDirectory(Some(Path("target")), "binny-fs-attr-", None)
      store = FsAttributeStore[IO](FsAttrConfig.default(dir))
    } yield store
  )

  override def munitFixtures: Seq[Fixture[_]] = List(attrStore)
}
