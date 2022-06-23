package binny.spec

import binny._
import cats.effect._
import cats.syntax.all._

abstract class ChunkedBinaryStoreSpec[S <: ChunkedBinaryStore[IO]]
    extends BinaryStoreSpec[S] {

  def computeAttr(id: BinaryId, store: S) =
    store
      .findBinary(id, ByteRange.All)
      .semiflatMap(
        _.through(
          ComputeAttr.computeAll(ContentTypeDetect.probeFileType, Hint.none)
        ).compile.lastOrError
      )

  test("insert chunks out of order") {
    val store = binStore()
    val chunks = ExampleData.file2M
      .chunkN(256 * 1024)
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
      attrs <- computeAttr(id, store).getOrElse(sys.error("not found"))
      _ = assertEquals(attrs, ExampleData.file2MAttr)
    } yield ()
  }

  test("insert chunks out of order concurrently") {
    val store = binStore()
    val chunks = ExampleData.file2M
      .chunkN(256 * 1024)
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
      attrs <- computeAttr(id, store).getOrElse(sys.error("not found"))
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
