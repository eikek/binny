package binny.fs

import binny.Log4sLogger
import binny.spec.BinaryStoreSpec
import cats.effect.IO
import fs2.io.file.{Files, Path}

class FsBinaryStoreTest extends BinaryStoreSpec[FsBinaryStore[IO]] {

  val binStore = ResourceSuiteLocalFixture(
    "fs-store",
    for {
      dir <- Files[IO].tempDirectory(Some(Path("target")), "binny-fs-bs-", None)
      logger = Log4sLogger[IO](org.log4s.getLogger("FsBinaryStore"))
      store = FsBinaryStore.default[IO](logger, dir)
    } yield store
  )

  override def munitFixtures: Seq[Fixture[_]] = List(binStore)
}
