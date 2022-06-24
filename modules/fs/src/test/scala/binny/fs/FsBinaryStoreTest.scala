package binny.fs

import binny.spec.BinaryStoreSpec
import binny.util.Logger
import cats.effect.IO
import fs2.io.file.{Files, Path}

class FsBinaryStoreTest extends BinaryStoreSpec[FsBinaryStore[IO]] {
  val logger = Logger.stdout[IO](Logger.Level.Warn, getClass.getSimpleName)

  val binStoreFixture = ResourceSuiteLocalFixture(
    "fs-store",
    for {
      dir <- Files[IO].tempDirectory(Some(Path("target")), "binny-fs-bs-", None)
      store = FsBinaryStore.default[IO](logger, dir)
    } yield store
  )

  override def munitFixtures: Seq[Fixture[_]] = List(binStoreFixture)
  override def binStore = binStoreFixture()
}
