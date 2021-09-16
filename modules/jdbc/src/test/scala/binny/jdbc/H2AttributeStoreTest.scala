package binny.jdbc

import binny.spec.BinaryAttributeStoreSpec
import cats.effect._

class H2AttributeStoreTest
    extends BinaryAttributeStoreSpec[JdbcAttributeStore[IO]]
    with DbFixtures {

  val attrStore: Fixture[JdbcAttributeStore[IO]] =
    ResourceSuiteLocalFixture(
      "h2-attr-store",
      Resource.pure(h2AttrStore("h2binattr", logger, JdbcAttrConfig.default))
    )

  override def munitFixtures: Seq[Fixture[_]] = List(attrStore)
}
