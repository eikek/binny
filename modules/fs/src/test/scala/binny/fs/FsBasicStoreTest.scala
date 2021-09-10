package binny.fs

import binny.BasicStoreSuite
import cats.effect.IO

class FsBasicStoreTest extends BasicStoreSuite[FsBinaryStore[IO]] with FsFixtures {}
