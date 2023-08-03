package binny.fs

import binny._
import binny.util.RangeCalc.Offsets
import binny.util.{Logger, RangeCalc, StreamUtil}
import cats.data.{Kleisli, OptionT}
import cats.effect._
import cats.syntax.all._
import fs2.io.file.{Files, Flags, Path}
import fs2.{Chunk, Stream}
import scodec.bits.ByteVector

/** Stores binaries in chunks in the filesystem. When reading all available chunks are
  * concatenated.
  */
class FsChunkedBinaryStore[F[_]: Async: Files](
    val config: FsChunkedStoreConfig,
    logger: Logger[F]
) extends ChunkedBinaryStore[F] {
  private val fsStore = FsBinaryStore[F](config.toStoreConfig, logger)

  override def insertChunk(
      id: BinaryId,
      chunkDef: ChunkDef,
      hint: Hint,
      data: ByteVector
  ): F[InsertChunkResult] =
    InsertChunkResult.validateChunk(chunkDef, config.chunkSize, data.length.toInt) match {
      case Some(bad) => bad.pure[F]
      case None =>
        val ch = chunkDef.fold(identity, _.toTotal(config.chunkSize))
        val chunkFileName = FsChunkedBinaryStore.fileName(ch.index)
        val file = config.targetDir(id) / chunkFileName
        val insert = Stream
          .chunk(Chunk.byteVector(data))
          .through(Impl.write(file, config.overwriteMode))
          .compile
          .drain

        val checkComplete = Files[F]
          .list(config.targetDir(id))
          .compile
          .count
          .map(chunks =>
            if (chunks >= ch.total) InsertChunkResult.complete
            else InsertChunkResult.incomplete
          )

        for {
          _ <- insert
          r <- checkComplete
          _ <- logger.trace(
            s"Inserted chunk ${ch.index + 1}/${ch.total} of size ${data.length}"
          )
        } yield r
    }

  def computeAttr(id: BinaryId, hint: Hint): ComputeAttr[F] =
    Kleisli { select =>
      val chunk0 = makeFile(id, 0)
      val ct = Impl.detectContentType(chunk0, config.detect, hint)
      OptionT.liftF(exists(id)).filter(identity).as(select).semiflatMap {
        case AttributeName.ContainsSha256(_) =>
          listChunkFiles(id, Offsets.none)
            .map(_._1)
            .flatMap(p => Files[F].readAll(p, config.readChunkSize, Flags.Read))
            .through(ComputeAttr.computeAll(config.detect, hint))
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
        val offsets = RangeCalc.calcOffset(range, config.chunkSize)
        val allChunks = listChunkFiles(id, offsets)
        if (offsets.isNone) {
          val contents = allChunks
            .map(_._1)
            .flatMap(p => Files[F].readAll(p, config.readChunkSize, Flags.Read))
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
                        config.readChunkSize,
                        start.toLong,
                        (start + end).toLong
                      )
                  case (Some(start), None) =>
                    Files[F].readRange(
                      chunkFile,
                      config.readChunkSize,
                      start.toLong,
                      Long.MaxValue
                    )
                  case (None, Some(end)) =>
                    Files[F].readRange(chunkFile, config.readChunkSize, 0, end.toLong)
                  case (None, None) =>
                    Files[F].readAll(chunkFile, config.readChunkSize, Flags.Read)
                }
              }
          )
        }
    }
  }

  def listIds(prefix: Option[String], chunkSize: Int): Stream[F, BinaryId] = {
    val firstChunkName = FsChunkedBinaryStore.fileName(0)
    val directoryDepth = config.targetDirDepth
    val all = Files[F]
      .walk(config.baseDir, directoryDepth, followLinks = false)
      .evalFilter(p => Files[F].isRegularFile(p / firstChunkName))
      .mapFilter(p => config.mapping.idFromDir(p))

    prefix.map(p => all.filter(_.id.startsWith(p))).getOrElse(all)
  }

  def insert = fsStore.insert

  def insertWith(id: BinaryId) = fsStore.insertWith(id)

  def exists(id: BinaryId): F[Boolean] =
    listChunkFiles(id, Offsets.oneChunk).take(1).compile.last.map(_.isDefined)

  def delete(id: BinaryId): F[Unit] = {
    val target = config.targetDir(id)
    Impl.deleteDir[F](target)
  }

  private def makeFile(id: BinaryId, chunkIndex: Int) =
    config.targetDir(id) / FsChunkedBinaryStore.fileName(chunkIndex)

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

  def apply[F[_]: Async: Files](
      logger: Logger[F],
      config: FsChunkedStoreConfig
  ): FsChunkedBinaryStore[F] =
    new FsChunkedBinaryStore[F](config, logger)

  def default[F[_]: Async: Files](
      logger: Logger[F],
      baseDir: Path
  ): FsChunkedBinaryStore[F] =
    apply(logger, FsChunkedStoreConfig.defaults(baseDir))
}
