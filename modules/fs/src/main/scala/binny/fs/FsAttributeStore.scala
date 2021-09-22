package binny.fs

import binny.{BinaryAttributeStore, BinaryAttributes, BinaryId}
import cats.data.OptionT
import cats.effect.Async
import cats.implicits._

final class FsAttributeStore[F[_]: Async](cfg: FsAttrConfig)
    extends BinaryAttributeStore[F] {

  def findAttr(id: BinaryId): OptionT[F, BinaryAttributes] = {
    val file = cfg.targetFile(id)
    Impl.loadAttrs[F](file)
  }

  def saveAttr(id: BinaryId, attrs: F[BinaryAttributes]): F[Unit] = {
    val file = cfg.targetFile(id)
    attrs.flatMap(a => Impl.writeAttrs[F](file, a))
  }

  def deleteAttr(id: BinaryId): F[Boolean] = {
    val file = cfg.targetFile(id)
    Impl.delete[F](file)
  }
}

object FsAttributeStore {
  def apply[F[_]: Async](cfg: FsAttrConfig): FsAttributeStore[F] =
    new FsAttributeStore[F](cfg)
}
