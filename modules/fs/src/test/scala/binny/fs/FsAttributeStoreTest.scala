package binny.fs

import binny.spec.BinaryAttributeStoreSpec
import cats.effect.IO

class FsAttributeStoreTest
    extends BinaryAttributeStoreSpec[FsAttributeStore[IO]]
    with FsFixtures {}
