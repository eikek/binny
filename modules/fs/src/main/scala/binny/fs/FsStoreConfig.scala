package binny.fs

import binny._
import fs2.io.file.Path

final case class FsStoreConfig(
    baseDir: Path,
    detect: ContentTypeDetect,
    overwriteMode: OverwriteMode,
    mapping: PathMapping,
    chunkSize: Int
) {

  def withContentTypeDetect(dt: ContentTypeDetect): FsStoreConfig =
    copy(detect = dt)

  def targetFile(id: BinaryId): Path =
    mapping.targetFile(baseDir, id)

  def withMapping(pm: PathMapping): FsStoreConfig =
    copy(mapping = pm)
}

object FsStoreConfig {

  def default(baseDir: Path): FsStoreConfig =
    FsStoreConfig(
      baseDir,
      ContentTypeDetect.probeFileType,
      OverwriteMode.Fail,
      PathMapping.subdir2("file"),
      100 * 1024
    )
}
