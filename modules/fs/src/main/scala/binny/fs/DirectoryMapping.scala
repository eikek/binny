package binny.fs

import binny._
import binny.fs.PathMapping.syntax._
import fs2.io.file.Path

trait DirectoryMapping {

  /** Determine the target directory using a base directory and the id. */
  def targetDir(base: Path, id: BinaryId): Path

  /** Given a directory, return the corresponding id */
  def idFromDir(dir: Path): Option[BinaryId]

  /** How many levels from `baseDir` to find the binary id directories. */
  def directoryDepth: Int

  private[fs] def toPathMapping(file: String): PathMapping =
    PathMapping((path, id) => targetDir(path, id) / file)(path =>
      path.parent.flatMap(idFromDir)
    )
}

object DirectoryMapping {

  def apply(depth: Int, f: (Path, BinaryId) => Path)(
      g: Path => Option[BinaryId]
  ): DirectoryMapping =
    new DirectoryMapping {
      def targetDir(base: Path, id: BinaryId) = f(base, id)
      def idFromDir(file: Path) = g(file)
      val directoryDepth = depth
    }

  /** Creates a subdirectory using the id. */
  val subdir: DirectoryMapping =
    DirectoryMapping(1, (base, id) => base / id.id)(file => Some(file.asId))

  /** Creates a subdirectory using the first two characters from the id. Below another
    * subdirectory using the complete id.
    */
  val subdir2: DirectoryMapping =
    DirectoryMapping(2, (base, id) => base / id.id.take(2) / id.id)(file =>
      Some(file.asId)
    )
}
