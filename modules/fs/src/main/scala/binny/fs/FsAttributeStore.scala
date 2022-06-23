package binny.fs

import binny._
import cats.data.OptionT
import cats.effect.Async
import cats.syntax.all._

final class FsAttributeStore[F[_]: Async](cfg: FsAttrConfig)
    extends BinaryAttributeStore[F] {

  def findAttr(id: BinaryId): OptionT[F, BinaryAttributes] = {
    val file = cfg.targetFile(id)
    Impl.loadAttrs[F](file)
  }

  def saveAttr(id: BinaryId, attrs: ComputeAttr[F]): F[Unit] = {
    val file = cfg.targetFile(id)
    attrs.run(AttributeName.all).semiflatMap(a => Impl.writeAttrs[F](file, a)).value.void
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
