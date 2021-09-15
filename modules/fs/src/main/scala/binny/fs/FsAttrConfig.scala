package binny.fs

import binny._
import fs2.io.file.Path

final case class FsAttrConfig(base: Path, mapping: PathMapping) {

  def targetFile(id: BinaryId): Path =
    mapping(base, id)

}

object FsAttrConfig {
  def default(base: Path) = FsAttrConfig(base, PathMapping.subdir2("attr"))
}
