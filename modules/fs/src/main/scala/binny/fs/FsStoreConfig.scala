package binny.fs

import binny._
import binny.fs.FsStoreConfig.{OverwriteMode, PathMapping}
import fs2.io.file.Path

final case class FsStoreConfig(
    baseDir: Path,
    detect: ContentTypeDetect,
    overwriteMode: OverwriteMode,
    mapping: PathMapping,
    chunkSize: Int
) {

  private[fs] def getTarget(id: BinaryId): Path =
    PathMapping.targetFile(mapping, baseDir, id)
}

object FsStoreConfig {

  def default(baseDir: Path): FsStoreConfig =
    FsStoreConfig(
      baseDir,
      ContentTypeDetect.none,
      OverwriteMode.Fail,
      PathMapping.Subdir,
      100 * 1024
    )

  sealed trait OverwriteMode
  object OverwriteMode {
    case object Fail    extends OverwriteMode
    case object Skip    extends OverwriteMode
    case object Replace extends OverwriteMode
  }

  sealed trait PathMapping
  object PathMapping {
    case object Basic extends PathMapping
    case object Subdir extends PathMapping {
      def attrFile(baseDir: Path, id: BinaryId): Path =
        baseDir / id.id.take(2) / id.id / "attrs"
    }

    private[fs] def targetFile(m: PathMapping, baseDir: Path, id: BinaryId): Path =
      m match {
        case Basic =>
          baseDir / id.id

        case Subdir =>
          baseDir / id.id.take(2) / id.id / "file"
      }
  }
}
