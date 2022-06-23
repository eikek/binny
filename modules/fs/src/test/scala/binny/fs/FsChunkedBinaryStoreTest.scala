package binny.fs

import binny.spec.ChunkedBinaryStoreSpec
import binny.util.Logger
import cats.effect._
import fs2.io.file.{Files, Path}

class FsChunkedBinaryStoreTest extends ChunkedBinaryStoreSpec[FsChunkedBinaryStore[IO]] {
  val logger = Logger.stdout[IO](Logger.Level.Warn, getClass.getSimpleName)

  val binStoreFixture = ResourceSuiteLocalFixture(
    "chunked-fs-store",
    for {
      dir <- Files[IO].tempDirectory(Some(Path("target")), "binny-fs-bs-", None)
      store = FsChunkedBinaryStore.default[IO](logger, dir)
    } yield store
  )

  override def munitFixtures: Seq[Fixture[_]] = List(binStoreFixture)
  override def binStore = binStoreFixture()
}
