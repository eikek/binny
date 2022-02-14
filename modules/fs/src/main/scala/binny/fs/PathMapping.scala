package binny.fs

import binny._
import cats.implicits._
import fs2.io.file.Path

trait PathMapping {

  /** Determine the target file using a base directory and the id. */
  def targetFile(base: Path, id: BinaryId): Path

  def idFromFile(file: Path): Option[BinaryId]

}

object PathMapping {
  import syntax._

  def apply(f: (Path, BinaryId) => Path)(g: Path => Option[BinaryId]): PathMapping =
    new PathMapping {
      def targetFile(base: Path, id: BinaryId) = f(base, id)
      def idFromFile(file: Path) = g(file)
    }

  /** This simply resolves the id against the base directory. */
  def simple: PathMapping =
    PathMapping((base, id) => base / id.id)(_.asId.some)

  /** Creates a subdirectory using the complete id, placing `filename` into it. */
  def subdir(filename: String): PathMapping =
    PathMapping((base, id) => base / id.id / filename)(file =>
      Option.when(file.isFilename(filename))(file.parentAsId).flatten
    )

  /** Creates a subdirectory using the first two characters from the id. Below another
    * subdirectory using the complete id and in there a file of `filename`.
    */
  def subdir2(filename: String): PathMapping =
    PathMapping((base, id) => base / id.id.take(2) / id.id / filename)(file =>
      Option.when(file.isFilename(filename))(file.parentAsId).flatten
    )

  object syntax {
    implicit class PathMappingOps(p: Path) {
      def isFilename(str: String): Boolean =
        p.fileName.toString == str

      def asId: BinaryId =
        BinaryId(p.fileName.toString)

      def parentAsId: Option[BinaryId] =
        p.parent.map(_.asId)
    }
  }
}
