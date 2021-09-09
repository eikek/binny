package binny.fs

import binny.ContentTypeDetect.Hint
import binny._
import binny.fs.FsStoreConfig.PathMapping
import cats.data.OptionT
import cats.effect._
import cats.implicits._
import fs2.io.file.Path

final class FsBinaryStore[F[_]: Async](
    val config: FsStoreConfig,
    attrStore: BinaryAttributeStore[F]
) extends BinaryStore[F]
    with ReadonlyAttributeStore[F] {

  def insertWith(data: BinaryData[F], hint: Hint): F[Unit] = {
    val target = config.getTarget(data.id)
    val stored = data.bytes
      .observe(Impl.write[F](target, config.overwriteMode))
      .through(BinaryAttributes.compute(config.detect, hint))
      .compile
      .lastOrError

    stored.flatMap(attrStore.saveAttr(data.id, _))
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

  def apply[F[_]: Async](cfg: FsStoreConfig): FsBinaryStore[F] = {
    val attrStore = cfg.mapping match {
      case PathMapping.Basic =>
        BinaryAttributeStore.empty[F]
      case m: PathMapping.Subdir.type =>
        FsAttributeStore(id => m.attrFile(cfg.baseDir, id))
    }
    new FsBinaryStore[F](cfg, attrStore)
  }

  def default[F[_]: Async](baseDir: Path): FsBinaryStore[F] =
    apply(FsStoreConfig.default(baseDir))
}
