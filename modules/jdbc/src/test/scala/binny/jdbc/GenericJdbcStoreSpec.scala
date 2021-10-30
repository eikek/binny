package binny.jdbc

import javax.sql.DataSource

import binny.Binary.Implicits._
import binny._
import binny.jdbc.impl.DbRunApi
import binny.jdbc.impl.Implicits._
import binny.spec.BinaryStoreSpec
import binny.util.Logger
import cats.effect.IO
import cats.implicits._

abstract class GenericJdbcStoreSpec extends BinaryStoreSpec[GenericJdbcStore[IO]] {

  val binStore2: Fixture[GenericJdbcStore[IO]]
  def logger: Logger[IO]
  def dataSource: DataSource

  test("different chunk size when storing and loading") {
    val store = binStore()
    val id = ExampleData.file2M
      .through(store.insert(Hint.none))
      .compile
      .lastOrError
      .unsafeRunSync()

    val loadStore = binStore2()
    val bin = loadStore
      .findBinary(id, ByteRange(300, 250))
      .getOrElse(sys.error("not found"))
      .unsafeRunSync()

    val expected =
      """ld 21
        |hello world 22
        |hello world 23
        |hello world 24
        |hello world 25
        |hello world 26
        |hello world 27
        |hello world 28
        |hello world 29
        |hello world 30
        |hello world 31
        |hello world 32
        |hello world 33
        |hello world 34
        |hello world 35
        |hello world 36
        |hello world 37
        |hell""".stripMargin

    assertIO(bin.readUtf8String, expected)
  }

  test("failing stream creates no data") {
    val store = binStore()
    val ds = dataSource // same ds as binStore()
    val attrDB = new DbRunApi[IO](JdbcAttrConfig.default.table, logger)
    val dataDB = new DbRunApi[IO](JdbcStoreConfig.default.dataTable, logger)
    for {
      attrCount <- attrDB.countAll().execute(ds)
      dataCount <- dataDB.countAll().execute(ds)
      idEither <- ExampleData.fail
        .through(store.insert(Hint.none))
        .attempt
        .compile
        .lastOrError
      _ = assert(idEither.isLeft)
      attrCount2 <- attrDB.countAll().execute(ds)
      dataCount2 <- dataDB.countAll().execute(ds)
      _ = assertEquals(attrCount2, attrCount)
      _ = assertEquals(dataCount2, dataCount)
    } yield ()
  }

  test("insert chunks out of order") {
    val store = binStore()
    val chunks = ExampleData.file2M
      .chunkN(JdbcStoreConfig.default.chunkSize)
      .zipWithIndex
      .compile
      .toVector
      .map(list => scala.util.Random.shuffle(list))

    for {
      ch <- chunks
      id <- BinaryId.random[IO]
      res <- ch.traverse { case (bytes, index) =>
        store.insertChunk(
          id,
          ChunkDef.fromTotal(index.toInt, ch.size),
          Hint.none,
          bytes.toByteVector
        )
      }
      _ = assertEquals(res.last, InsertChunkResult.Complete)
      _ = assertEquals(res.init.toSet, Set(InsertChunkResult.incomplete))
      attrs <- store.computeAttr(id, Hint.none).getOrElse(sys.error("not found"))
      _ = assertEquals(attrs, ExampleData.file2MAttr)
    } yield ()
  }

  test("insert chunks out of order concurrently") {
    val store = binStore()
    val chunks = ExampleData.file2M
      .chunkN(JdbcStoreConfig.default.chunkSize)
      .zipWithIndex
      .compile
      .toVector
      .map(list => scala.util.Random.shuffle(list))

    def insertConcurrent(id: BinaryId) =
      fs2.Stream
        .eval(chunks.map(v => v.map(t => (t._1, t._2, v.size))))
        .flatMap(v => fs2.Stream.emits(v))
        .parEvalMapUnordered(4) { case (bytes, index, size) =>
          store.insertChunk(
            id,
            ChunkDef.fromTotal(index.toInt, size),
            Hint.none,
            bytes.toByteVector
          )
        }

    for {
      id <- BinaryId.random[IO]
      res <- insertConcurrent(id).compile.toVector
      attrs <- store.computeAttr(id, Hint.none).getOrElse(sys.error("not found"))
      _ = assertEquals(attrs, ExampleData.file2MAttr)
      _ = assertEquals(
        res.filter(_ == InsertChunkResult.Complete).toSet,
        Set(InsertChunkResult.complete)
      )
      _ = assertEquals(
        res.filter(_ != InsertChunkResult.Complete).toSet,
        Set(InsertChunkResult.incomplete)
      )
    } yield ()
  }
}
