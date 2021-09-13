package binny.fs

import binny.Log4sLogger
import cats.effect.IO
import fs2.io.file.{Files, Path}
import munit.CatsEffectSuite

trait FsFixtures { self: CatsEffectSuite =>

  lazy val binStore = ResourceFixture(for {
    dir <- Files[IO].tempDirectory(Some(Path("target")), "binny-fs-bs-", None)
    logger = Log4sLogger[IO](org.log4s.getLogger("FsBinaryStore"))
    store = FsBinaryStore.default[IO](logger, dir)
  } yield store)

  lazy val attrStore = ResourceFixture(for {
    dir <- Files[IO].tempDirectory(Some(Path("target")), "binny-fs-attr-", None)
    store = FsAttributeStore[IO](id => dir / id.id)
  } yield store)

}
