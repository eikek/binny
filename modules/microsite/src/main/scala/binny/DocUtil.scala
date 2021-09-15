package binny

import cats.effect.IO
import fs2.Stream
import fs2.io.file.{Files, Path}

object DocUtil {

  def directoryContent(path: Path, level: Int = 0): Stream[IO, String] =
    Stream.eval(indent(path, level)) ++ Files[IO].list(path).flatMap { child =>
      Stream
        .eval(Files[IO].isDirectory(child))
        .flatMap(isDir =>
          if (isDir) directoryContent(child, level + 1)
          else Stream.eval(indent(child, level + 1))
        )
    }

  def directoryContentAsString(path: Path): IO[String] =
    directoryContent(path, 0)
      .intersperse("\n")
      .foldMonoid
      .compile
      .lastOrError
      .map("\n" + _)

  private def indent(path: Path, n: Int): IO[String] = {
    val spaces = Stream.emit(" ").repeat.take(n * 2).compile.foldMonoid
    Files[IO].isDirectory(path).map {
      case true  => spaces + "📁 " + path.fileName.toString
      case false => spaces + "· " + path.fileName.toString
    }

  }
}
