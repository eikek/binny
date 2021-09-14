package binny.fs

import binny.spec.BinaryStoreSpec
import cats.effect.IO

class FsBasicStoreTest extends BinaryStoreSpec[FsBinaryStore[IO]] with FsFixtures {}
