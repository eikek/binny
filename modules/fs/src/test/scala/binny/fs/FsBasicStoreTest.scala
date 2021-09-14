package binny.fs

import binny.spec.{BinaryAttributeStore2Spec, BinaryStore2Spec}
import cats.effect.IO
import munit.CatsEffectSuite

class FsBasicStoreTest
    extends CatsEffectSuite
    with BinaryStore2Spec[FsBinaryStore[IO]]
    with BinaryAttributeStore2Spec[FsAttributeStore[IO]]
    with FsFixtures {

  override def munitFixtures: Seq[Fixture[_]] = List(binStore, attrStore)
}
