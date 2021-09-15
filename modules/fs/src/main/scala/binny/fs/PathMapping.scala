package binny.fs

import binny._
import fs2.io.file.Path

/** Determine the target file using a base directory and the id. */
trait PathMapping extends ((Path, BinaryId) => Path) {}

object PathMapping {

  def apply(f: (Path, BinaryId) => Path): PathMapping =
    (base, id) => f(base, id)

  /** This simply resolves the id against the base directory. */
  def simple: PathMapping =
    (base, id) => base / id.id

  /** Creates a subdirectory using the complete id, placing `filename` into it. */
  def subdir(filename: String): PathMapping =
    (base, id) => base / id.id / filename

  /** Creates a subdirectory using the first two characters from the id. Below another
    * subdirectory using the complete id and in there a file of `filename`.
    */
  def subdir2(filename: String): PathMapping =
    (base, id) => base / id.id.take(2) / id.id / filename
}
