package binny.jdbc

import binny.Log4sLogger
import binny.spec.{BinaryAttributeStoreSpec, BinaryStoreSpec}
import cats.effect._
import munit.CatsEffectSuite

class H2BinaryStoreTest
    extends CatsEffectSuite
    with BinaryStoreSpec[JdbcBinaryStore[IO]]
    with BinaryAttributeStoreSpec[JdbcAttributeStore[IO]]
    with DbFixtures {

  lazy val binStore: Fixture[JdbcBinaryStore[IO]] =
    ResourceSuiteLocalFixture(
      "h2-store",
      Resource.pure(
        h2BinStore("h2bin", Log4sLogger(org.log4s.getLogger), JdbcStoreConfig.default)
      )
    )

  lazy val attrStore: Fixture[JdbcAttributeStore[IO]] =
    ResourceSuiteLocalFixture(
      "h2-attr-store",
      Resource.pure(h2AttrStore("h2binattr", logger, JdbcAttrConfig.default))
    )

  override def munitFixtures: Seq[Fixture[_]] = List(binStore, attrStore)
}
