package binny.jdbc

import binny.Log4sLogger
import binny.spec.BinaryAttributeStoreSpec
import cats.effect._

class H2BinaryAttributeStoreTest
    extends BinaryAttributeStoreSpec[JdbcAttributeStore[IO]]
    with DbFixtures {
  lazy val attrStore: SyncIO[FunFixture[JdbcAttributeStore[IO]]] =
    SyncIO(
      h2AttrStore(
        Dbms.PostgreSQL,
        Log4sLogger(org.log4s.getLogger),
        JdbcAttrConfig.default
      )
    )

}
