package binny.fs

import binny.ContentTypeDetect.Hint
import binny._
import binny.fs.FsStoreConfig.PathMapping
import binny.util.{Logger, Stopwatch}
import cats.data.OptionT
import cats.effect._
import cats.implicits._
import fs2.io.file.{Files, Path}

final class FsBinaryStore[F[_]: Async](
    val config: FsStoreConfig,
    logger: Logger[F],
    attrStore: BinaryAttributeStore[F]
) extends BinaryStore[F]
    with ReadonlyAttributeStore[F] {

  def insertWith(data: BinaryData[F], hint: Hint): F[Unit] = {
    val target = config.getTarget(data.id)
    val stored =
      data.bytes.through(Impl.write[F](target, config.overwriteMode)).compile.drain
    // tried with .observe to consume the stream once, but it took 5x longer
    val attr = Files[F]
      .readAll(target)
      .through(BinaryAttributes.compute(config.detect, hint))
      .compile
      .lastOrError

    for {
      _ <- logger.debug(s"Insert file with id ${data.id.id}")
      w <- Stopwatch.start[F]
      _ <- stored
      _ <- attr.flatMap(attrStore.saveAttr(data.id, _))
      _ <- Stopwatch.show(w)(d => logger.debug(s"Inserted file ${data.id.id} in $d"))
    } yield ()
  }

  def load(id: BinaryId, range: ByteRange, chunkSize: Int): OptionT[F, BinaryData[F]] = {
    val target = config.getTarget(id)
    Impl.load[F](id, target, range, chunkSize)
  }

  def delete(id: BinaryId): F[Boolean] = {
    val target = config.getTarget(id)
    attrStore.deleteAttr(id) *> Impl.delete[F](target)
  }

  def findAttr(id: BinaryId): OptionT[F, BinaryAttributes] =
    attrStore.findAttr(id)
}

object FsBinaryStore {

  def apply[F[_]: Async](logger: Logger[F], cfg: FsStoreConfig): FsBinaryStore[F] = {
    val attrStore = cfg.mapping match {
      case PathMapping.Basic =>
        BinaryAttributeStore.empty[F]
      case m: PathMapping.Subdir.type =>
        FsAttributeStore(id => m.attrFile(cfg.baseDir, id))
    }
    new FsBinaryStore[F](cfg, logger, attrStore)
  }

  def default[F[_]: Async](logger: Logger[F], baseDir: Path): FsBinaryStore[F] =
    apply(logger, FsStoreConfig.default(baseDir))
}
