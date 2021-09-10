package binny.fs

import binny.BasicAttributeStoreSuite
import cats.effect.IO

class FsAttributeStoreTest
    extends BasicAttributeStoreSuite[FsAttributeStore[IO]]
    with FsFixtures {}
