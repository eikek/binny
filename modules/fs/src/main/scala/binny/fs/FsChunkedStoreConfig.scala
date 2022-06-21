package binny.fs

import binny.{BinaryId, ContentTypeDetect}
import fs2.io.file.Path

/** @param baseDir
  *   the base directory, everything is stored below
  * @param detect
  *   detect the content type of a binary when storing
  * @param overwriteMode
  *   what to do when a file already exists
  * @param mapping
  *   the mapping from a BinaryId to a directory containing all the chunk files
  * @param chunkSize
  *   the size of each chunk file
  * @param readChunkSize
  *   used when reading chunk files, this may be set at maximum to `chunkSize`, but can be
  *   set smaller to use less memory when reading files
  */
case class FsChunkedStoreConfig(
    baseDir: Path,
    detect: ContentTypeDetect,
    overwriteMode: OverwriteMode,
    mapping: DirectoryMapping,
    chunkSize: Int,
    readChunkSize: Int
) {

  def withContentTypeDetect(dt: ContentTypeDetect): FsChunkedStoreConfig =
    copy(detect = dt)

  def targetDir(id: BinaryId): Path =
    mapping.targetDir(baseDir, id)

  def targetDirDepth =
    targetDir(BinaryId("dummy")).relativize(baseDir).names.size

  def withMapping(dm: DirectoryMapping): FsChunkedStoreConfig =
    copy(mapping = dm)

  private[fs] def toStoreConfig: FsStoreConfig =
    FsStoreConfig(
      baseDir = baseDir,
      detect = detect,
      overwriteMode = overwriteMode,
      mapping = mapping.toPathMapping(FsChunkedBinaryStore.fileName(0)),
      chunkSize = chunkSize
    )
}
object FsChunkedStoreConfig {

  def defaults(baseDir: Path) =
    FsChunkedStoreConfig(
      baseDir = baseDir,
      detect = ContentTypeDetect.probeFileType,
      overwriteMode = OverwriteMode.Fail,
      mapping = DirectoryMapping.subdir2,
      chunkSize = 256 * 1024,
      readChunkSize = 128 * 1024
    )
}
