package binny.jdbc

import binny.spec.BinaryAttributeStoreSpec
import binny.util.Logger
import cats.effect._

class H2AttributeStoreTest
    extends BinaryAttributeStoreSpec[JdbcAttributeStore[IO]]
    with DbFixtures {
  val logger = Logger.stdout[IO](Logger.Level.Off, getClass.getSimpleName)

  val attrStore: Fixture[JdbcAttributeStore[IO]] =
    ResourceSuiteLocalFixture(
      "h2-attr-store",
      Resource.pure(h2AttrStore("h2binattr", logger, JdbcAttrConfig.default))
    )

  override def munitFixtures: Seq[Fixture[_]] = List(attrStore)
}
