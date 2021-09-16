package binny.jdbc

import binny.Log4sLogger
import binny.spec.BinaryStoreSpec
import cats.effect._

class H2BinaryStoreTest extends BinaryStoreSpec[JdbcBinaryStore[IO]] with DbFixtures {

  val binStore: Fixture[JdbcBinaryStore[IO]] =
    ResourceSuiteLocalFixture(
      "h2-store",
      Resource.pure(
        h2BinStore("h2bin", Log4sLogger(org.log4s.getLogger), JdbcStoreConfig.default)
      )
    )

  override def munitFixtures: Seq[Fixture[_]] = List(binStore)
}
