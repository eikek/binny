package binny

import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.implicits._
import fs2.{Chunk, Pipe, Stream}
import scodec.bits.ByteVector

class MemoryBinaryStore[F[_]: Sync](
    data: Ref[F, Map[BinaryId, ByteVector]],
    detect: ContentTypeDetect = ContentTypeDetect.probeFileType
) extends BinaryStore[F] {

  def listIds(prefix: Option[String], chunkSize: Int): Stream[F, BinaryId] = {
    val all = Stream.eval(data.get).flatMap(m => Stream.emits(m.keySet.toList))
    prefix.map(p => all.filter(id => id.id.startsWith(p))).getOrElse(all)
  }

  def insert: Pipe[F, Byte, BinaryId] =
    in =>
      Stream
        .eval(BinaryId.random[F])
        .flatMap(id => in.through(insertWith(id)) ++ Stream.emit(id))

  def insertWith(id: BinaryId): Pipe[F, Byte, Nothing] =
    in => {
      val bytes = in.chunks.map(_.toByteVector).compile.fold(ByteVector.empty)(_ ++ _)
      Stream.eval(bytes.flatMap(bs => data.update(_.updated(id, bs)))).drain
    }

  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]] =
    for {
      bytes <- OptionT(data.get.map(_.get(id)))
      res = range match {
        case ByteRange.All =>
          bytes
        case ByteRange.Chunk(offset, len) =>
          bytes.drop(offset).take(len)
      }
      stream = Stream.chunk(Chunk.byteVector(res))
    } yield stream

  def exists(id: BinaryId): F[Boolean] =
    data.get.map(_.contains(id))

  def delete(id: BinaryId): F[Unit] =
    data.update(_.removed(id))

  def computeAttr(id: BinaryId, hint: Hint) = Kleisli { _ =>
    OptionT(data.get.map(_.get(id)))
      .map(bv =>
        BinaryAttributes(
          sha256 = bv.sha256,
          contentType = detect.detect(bv, hint),
          length = bv.length
        )
      )
  }
}

object MemoryBinaryStore {

  def createEmpty[F[_]: Sync]: F[MemoryBinaryStore[F]] =
    create(Map.empty)

  def create[F[_]: Sync](data: Map[BinaryId, ByteVector]): F[MemoryBinaryStore[F]] =
    Ref.of[F, Map[BinaryId, ByteVector]](data).map(new MemoryBinaryStore(_))
}
