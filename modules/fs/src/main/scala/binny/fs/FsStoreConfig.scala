package binny.fs

import binny._
import binny.fs.FsStoreConfig.OverwriteMode
import fs2.io.file.Path

final case class FsStoreConfig(
    baseDir: Path,
    detect: ContentTypeDetect,
    overwriteMode: OverwriteMode,
    mapping: PathMapping,
    chunkSize: Int
) {

  def targetFile(id: BinaryId): Path =
    mapping(baseDir, id)

  def withMapping(pm: PathMapping): FsStoreConfig =
    copy(mapping = pm)
}

object FsStoreConfig {

  def default(baseDir: Path): FsStoreConfig =
    FsStoreConfig(
      baseDir,
      ContentTypeDetect.none,
      OverwriteMode.Fail,
      PathMapping.subdir2("file"),
      100 * 1024
    )

  sealed trait OverwriteMode
  object OverwriteMode {
    case object Fail extends OverwriteMode
    case object Skip extends OverwriteMode
    case object Replace extends OverwriteMode
  }
}
