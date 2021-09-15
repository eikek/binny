package binny.fs

import binny.spec.{BinaryAttributeStoreSpec, BinaryStoreSpec}
import cats.effect.IO
import munit.CatsEffectSuite

class FsBinaryStoreTest
    extends CatsEffectSuite
    with BinaryStoreSpec[FsBinaryStore[IO]]
    with BinaryAttributeStoreSpec[FsAttributeStore[IO]]
    with FsFixtures {

  override def munitFixtures: Seq[Fixture[_]] = List(binStore, attrStore)
}
