package binny.fs

import binny.{BinaryAttributeStore, BinaryAttributes, BinaryId}
import cats.data.OptionT
import cats.effect.Async
import cats.implicits._
import fs2.io.file.Path

final class FsAttributeStore[F[_]: Async](getFile: BinaryId => Path)
    extends BinaryAttributeStore[F] {

  def findAttr(id: BinaryId): OptionT[F, BinaryAttributes] = {
    val file = getFile(id)
    Impl.loadAttrs[F](file)
  }

  def saveAttr(id: BinaryId, attrs: F[BinaryAttributes]): F[Unit] = {
    val file = getFile(id)
    attrs.flatMap(a => Impl.writeAttrs[F](file, a))
  }

  def deleteAttr(id: BinaryId): F[Boolean] = {
    val file = getFile(id)
    Impl.delete[F](file)
  }
}

object FsAttributeStore {
  def apply[F[_]: Async](getFile: BinaryId => Path): FsAttributeStore[F] =
    new FsAttributeStore[F](getFile)
}
