package binny

import binny.minio._
import cats.effect._
import com.dimafeng.testcontainers.{GenericContainer, PostgreSQLContainer}
import fs2.Stream
import fs2.io.file.{Files, Path}
import org.testcontainers.utility.DockerImageName

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

  def deleteDir(dir: Path): IO[Unit] =
    Files[IO].exists(dir).flatMap { exists =>
      if (exists) Files[IO].deleteRecursively(dir)
      else IO(())
    }

  def tempDir: Resource[IO, Path] =
    Files[IO].tempDirectory(None, "binny-docs-", None)

  def startPostgresContainer: Resource[IO, PostgreSQLContainer] = {
    def create = {
      val cnt = PostgreSQLContainer(DockerImageName.parse("postgres:13"))
      cnt.start()
      cnt
    }
    Resource.make(IO(create))(cnt => IO(cnt.stop()))
  }

  def startMinIOContainer: Resource[IO, MinioContainer.MinioCnt] =
    Resource
      .make(IO(new MinioContainer.Def.start()))(cnt => IO(cnt.stop()))
}
