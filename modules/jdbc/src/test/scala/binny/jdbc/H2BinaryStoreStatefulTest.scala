package binny.jdbc

import binny.spec.BinaryStoreSpec
import binny.{BinaryStore, Log4sLogger}
import cats.effect._
import munit.CatsEffectSuite

class H2BinaryStoreStatefulTest
    extends CatsEffectSuite
    with BinaryStoreSpec[BinaryStore[IO]]
    with DbFixtures {

  lazy val binStore: Fixture[BinaryStore[IO]] =
    ResourceSuiteLocalFixture(
      "h2-store",
      Resource.pure(
        SwapFind(
          h2BinStore("h2bin2", Log4sLogger(org.log4s.getLogger), JdbcStoreConfig.default)
        )
      )
    )

  override def munitFixtures: Seq[Fixture[_]] = List(binStore)
}
