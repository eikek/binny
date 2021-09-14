package binny.jdbc

import binny.Log4sLogger
import binny.spec.BinaryStoreSpec
import cats.effect._

class H2BinaryStoreTest extends BinaryStoreSpec[JdbcBinaryStore[IO]] with DbFixtures {
  lazy val binStore: SyncIO[FunFixture[JdbcBinaryStore[IO]]] =
    SyncIO(h2BinStore(Log4sLogger(org.log4s.getLogger), JdbcStoreConfig.default))
}
