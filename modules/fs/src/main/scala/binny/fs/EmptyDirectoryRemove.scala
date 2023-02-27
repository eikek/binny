package binny.fs

import binny.BinaryId
import binny.fs.EmptyDirectoryRemove.Dir
import binny.util.Logger
import cats.effect._
import cats.syntax.all._
import fs2.Stream
import fs2.io.file.{Files, NoSuchFileException, Path}

final class EmptyDirectoryRemove[F[_]: Files: Sync](
    val baseDir: Path,
    targetFile: BinaryId => Path,
    logger: Logger[F]
) {

  def removeEmptyDirs(id: BinaryId): F[Unit] = {
    val file = targetFile(id)
    dirsToRemove(file)
      .evalMap(p => isEmptyDirectory(p).map(Dir(p, _)))
      .takeWhile(_.isEmpty)
      .map(_.path)
      .evalMap(p => Files[F].deleteIfExists(p).map(p -> _))
      .evalTap { case (p, ok) =>
        if (ok) logger.debug(s"Removed empty directory: $p") else ().pure[F]
      }
      .compile
      .drain
  }

  private def isEmptyDirectory(p: Path): F[Boolean] =
    Files[F]
      .list(p)
      .head
      .compile
      .last
      .map(_.isEmpty)
      .recover { case _: NoSuchFileException =>
        false
      }

  private def dirsToRemove(dir: Path): Stream[F, Path] =
    parentStream(dir.some)
      .filter(_.startsWith(baseDir))
      .filter(_ != baseDir)
      .evalFilter(Files[F].isDirectory)

  private def parentStream(dir: Option[Path]): Stream[F, Path] =
    dir match {
      case None    => Stream.empty
      case Some(p) => Stream.emit(p) ++ parentStream(p.parent)
    }
}

object EmptyDirectoryRemove {

  def apply[F[_]: Files: Sync: Logger](config: FsStoreConfig): EmptyDirectoryRemove[F] =
    new EmptyDirectoryRemove[F](config.baseDir, config.targetFile, Logger[F])

  def apply[F[_]: Files: Sync: Logger](
      config: FsChunkedStoreConfig
  ): EmptyDirectoryRemove[F] =
    new EmptyDirectoryRemove[F](config.baseDir, config.targetDir, Logger[F])

  private case class Dir(path: Path, isEmpty: Boolean)
}
