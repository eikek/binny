package binny.fs

import binny._
import binny.util.{Logger, RangeCalc}
import cats.data.OptionT
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
    logger: Logger[F],
    attrStore: BinaryAttributeStore[F]
) extends ChunkedBinaryStore[F] {
  private val fsStore = FsBinaryStore[F](cfg.toStoreConfig, logger, attrStore)

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
          _ <-
            if (r == InsertChunkResult.Complete)
              attrStore.saveAttr(id, computeAttr(id, hint))
            else ().pure[F]
        } yield r
    }

  def computeAttr(id: BinaryId, hint: Hint): F[BinaryAttributes] =
    listChunkFiles(id)
      .evalMap(Impl.loadAll[F])
      .fold(BinaryAttributes.State.empty)(_.update(cfg.detect, hint, _))
      .map(_.toAttributes)
      .compile
      .lastOrError

  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]] = {
    val listing = listChunkFiles(id).take(2).compile.toList
    OptionT.liftF(listing).flatMap {
      case Nil =>
        OptionT.none[F, Binary[F]]

      case _ :: Nil =>
        fsStore.findBinary(id, range)

      case _ =>
        val allChunks =
          Stream.evals(
            listChunkFiles(id).compile.toVector
              .map(_.sortBy(_.fileName.toString))
          )
        val offsets = RangeCalc.calcOffset(range, cfg.chunkSize)
        if (offsets.isNone) {
          val contents = allChunks.flatMap(p => Files[F].readAll(p))
          OptionT.pure(contents)
        } else {
          OptionT.pure(
            allChunks.zipWithIndex
              .drop(offsets.firstChunk)
              .take(offsets.takeChunks)
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
    val all = Files[F]
      .walk(cfg.baseDir, cfg.mapping.directoryDepth, followLinks = false)
      .evalFilter(p => Files[F].isRegularFile(p / firstChunkName))
      .mapFilter(p => cfg.mapping.idFromDir(p))

    prefix.map(p => all.filter(_.id.startsWith(p))).getOrElse(all)
  }

  def insert(hint: Hint) = fsStore.insert(hint)

  def insertWith(id: BinaryId, hint: Hint) = fsStore.insertWith(id, hint)

  def exists(id: BinaryId): F[Boolean] =
    listChunkFiles(id).take(1).compile.last.map(_.isDefined)

  def delete(id: BinaryId): F[Unit] = {
    val target = cfg.targetDir(id)
    attrStore.deleteAttr(id) *> Impl.deleteDir[F](target)
  }

  private def listChunkFiles(id: BinaryId) = {
    val target = cfg.targetDir(id)
    for {
      exists <- Stream.eval(Files[F].isDirectory(target))
      res <-
        if (exists)
          Files[F]
            .list(cfg.targetDir(id))
            .filter(p => p.fileName.toString.startsWith("chunk_"))
        else Stream.empty
    } yield res
  }
}

object FsChunkedBinaryStore {
  private[fs] def fileName(n: Int): String = f"chunk_$n%08d"

  def apply[F[_]: Async](
      config: FsChunkedStoreConfig,
      logger: Logger[F],
      attrStore: BinaryAttributeStore[F]
  ): FsChunkedBinaryStore[F] =
    new FsChunkedBinaryStore[F](config, logger, attrStore)

  def apply[F[_]: Async](
      logger: Logger[F],
      storeCfg: FsChunkedStoreConfig,
      attrCfg: FsAttrConfig
  ): FsChunkedBinaryStore[F] = {
    val attrStore = FsAttributeStore(attrCfg)
    new FsChunkedBinaryStore[F](storeCfg, logger, attrStore)
  }

  def default[F[_]: Async](logger: Logger[F], baseDir: Path): FsChunkedBinaryStore[F] =
    apply(logger, FsChunkedStoreConfig.defaults(baseDir), FsAttrConfig.default(baseDir))
}
