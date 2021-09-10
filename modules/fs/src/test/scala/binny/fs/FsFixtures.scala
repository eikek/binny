package binny.fs

import cats.effect.IO
import fs2.io.file.{Files, Path}
import munit.CatsEffectSuite

trait FsFixtures { self: CatsEffectSuite =>

  lazy val binStore = ResourceFixture(for {
    dir <- Files[IO].tempDirectory(Some(Path("target")), "binny-fs-bs-", None)
    store = FsBinaryStore.default[IO](dir)
  } yield store)

  lazy val attrStore = ResourceFixture(for {
    dir <- Files[IO].tempDirectory(Some(Path("target")), "binny-fs-attr-", None)
    store = FsAttributeStore[IO](id => dir / id.id)
  } yield store)

}
