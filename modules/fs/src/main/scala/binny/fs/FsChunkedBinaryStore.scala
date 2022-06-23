package binny.fs

import binny._
import binny.util.RangeCalc.Offsets
import binny.util.{Logger, RangeCalc, StreamUtil}
import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.syntax.all._
import fs2.io.file.{Files, Path}
import fs2.{Chunk, Stream}
import scodec.bits.ByteVector

/** Stores binaries in chunks in the filesystem. When reading all available chunks are
  * concatenated.
  */
class FsChunkedBinaryStore[F[_]: Async](
    cfg: FsChunkedStoreConfig,
    logger: Logger[F]
) extends ChunkedBinaryStore[F] {
  private val fsStore = FsBinaryStore[F](cfg.toStoreConfig, logger)

  override def insertChunk(
      id: BinaryId,
      chunkDef: ChunkDef,
      hint: Hint,
      data: ByteVector
  ): F[InsertChunkResult] =
    InsertChunkResult.validateChunk(chunkDef, cfg.chunkSize, data.length.toInt) match {
      case Some(bad) => bad.pure[F]
      case None =>
        val ch = chunkDef.fold(identity, _.toTotal(cfg.chunkSize))
        val chunkFileName = FsChunkedBinaryStore.fileName(ch.index)
        val file = cfg.targetDir(id) / chunkFileName
        val insert = Stream
          .chunk(Chunk.byteVector(data))
          .through(Impl.write(file, cfg.overwriteMode))
          .compile
          .drain

        val checkComplete = Files[F]
          .list(cfg.targetDir(id))
          .compile
          .count
          .map(chunks =>
            if (chunks >= ch.total) InsertChunkResult.complete
            else InsertChunkResult.incomplete
          )

        for {
          _ <- logger.trace(
            s"Insert chunk ${ch.index + 1}/${ch.total} of size ${data.length}"
          )
          _ <- insert
          r <- checkComplete
        } yield r
    }

  def computeAttr(id: BinaryId, hint: Hint): ComputeAttr[F] =
    Kleisli { select =>
      val chunk0 = makeFile(id, 0)
      val ct = Impl.detectContentType(chunk0, cfg.detect, hint)
      OptionT.liftF(exists(id)).filter(identity).as(select).semiflatMap {
        case AttributeName.ContainsSha256(_) =>
          listChunkFiles(id, Offsets.none)
            .map(_._1)
            .flatMap(p => Files[F].readAll(p))
            .through(ComputeAttr.computeAll(cfg.detect, hint))
            .compile
            .lastOrError

        case AttributeName.ContainsLength(_) =>
          val len = listChunkFiles(id, Offsets.none)
            .map(_._1)
            .evalMap(Files[F].size)
            .compile
            .fold(0L)(_ + _)

          (ct, len).mapN(BinaryAttributes.apply)

        case _ =>
          ct.map(c => BinaryAttributes.empty.copy(contentType = c))
      }
    }

  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]] = {
    val listing = listChunkFiles(id, Offsets.chunks(2)).take(2).compile.toList
    OptionT.liftF(listing).flatMap {
      case Nil =>
        OptionT.none[F, Binary[F]]

      case _ :: Nil =>
        fsStore.findBinary(id, range)

      case _ =>
        val offsets = RangeCalc.calcOffset(range, cfg.chunkSize)
        val allChunks = listChunkFiles(id, offsets)
        if (offsets.isNone) {
          val contents = allChunks.map(_._1).flatMap(p => Files[F].readAll(p))
          OptionT.pure(contents)
        } else {
          OptionT.pure(
            allChunks
              .flatMap { case (chunkFile, index) =>
                RangeCalc.chopOffsets(offsets, index.toInt) match {
                  case (Some(start), Some(end)) =>
                    Files[F]
                      .readRange(
                        chunkFile,
                        cfg.readChunkSize,
                        start.toLong,
                        (start + end).toLong
                      )
                  case (Some(start), None) =>
                    Files[F].readRange(
                      chunkFile,
                      cfg.readChunkSize,
                      start.toLong,
                      Long.MaxValue
                    )
                  case (None, Some(end)) =>
                    Files[F].readRange(chunkFile, cfg.readChunkSize, 0, end.toLong)
                  case (None, None) =>
                    Files[F].readAll(chunkFile)
                }
              }
          )
        }
    }
  }

  def listIds(prefix: Option[String], chunkSize: Int): Stream[F, BinaryId] = {
    val firstChunkName = FsChunkedBinaryStore.fileName(0)
    val directoryDepth = cfg.targetDirDepth
    val all = Files[F]
      .walk(cfg.baseDir, directoryDepth, followLinks = false)
      .evalFilter(p => Files[F].isRegularFile(p / firstChunkName))
      .mapFilter(p => cfg.mapping.idFromDir(p))

    prefix.map(p => all.filter(_.id.startsWith(p))).getOrElse(all)
  }

  def insert = fsStore.insert

  def insertWith(id: BinaryId) = fsStore.insertWith(id)

  def exists(id: BinaryId): F[Boolean] =
    listChunkFiles(id, Offsets.oneChunk).take(1).compile.last.map(_.isDefined)

  def delete(id: BinaryId): F[Unit] = {
    val target = cfg.targetDir(id)
    Impl.deleteDir[F](target)
  }

  private def makeFile(id: BinaryId, chunkIndex: Int) =
    cfg.targetDir(id) / FsChunkedBinaryStore.fileName(chunkIndex)

  private def takeWhileExists(files: Stream[F, Path]): Stream[F, Path] =
    files
      .evalMap(p => Files[F].exists(p).map(_ -> p))
      .takeWhile(_._1)
      .map(_._2)

  private def listChunkFiles(id: BinaryId, offsets: Offsets) =
    if (offsets.isNone)
      Stream
        .iterate(0)(_ + 1)
        .map(n => makeFile(id, n))
        .through(takeWhileExists)
        .zipWithIndex
    else
      Stream
        .iterate(offsets.firstChunk)(_ + 1)
        .take(offsets.takeChunks)
        .map(n => makeFile(id, n))
        .through(takeWhileExists)
        .through(StreamUtil.zipWithIndexFrom(offsets.firstChunk))
}

object FsChunkedBinaryStore {
  private[fs] def fileName(n: Int): String = f"chunk_$n%08d"

  def apply[F[_]: Async](
      logger: Logger[F],
      config: FsChunkedStoreConfig
  ): FsChunkedBinaryStore[F] =
    new FsChunkedBinaryStore[F](config, logger)

  def default[F[_]: Async](logger: Logger[F], baseDir: Path): FsChunkedBinaryStore[F] =
    apply(logger, FsChunkedStoreConfig.defaults(baseDir))
}
