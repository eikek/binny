package binny.fs

import binny.BinaryId
import binny.util.Logger
import cats.effect.IO
import cats.syntax.all._
import fs2.io.file.{Files, Path}
import munit.CatsEffectSuite

class EmptyDirectoryRemoveTest extends CatsEffectSuite {
  implicit val logger: Logger[IO] = Logger.silent[IO]

  val testDirectory = ResourceSuiteLocalFixture[Path](
    "test-directory",
    Files[IO].tempDirectory(Some(Path("target")), "empty-dir-test-", None)
  )

  override def munitFixtures = List(testDirectory)

  def createEmptyDirRemove(mapping: PathMapping) = for {
    testDir <- IO(testDirectory())
    baseDir = testDir / "%.0f".format(math.random() * 1_000_000)
    _ <- Files[IO].createDirectories(baseDir)
    result = new EmptyDirectoryRemove[IO](
      FsStoreConfig.default(baseDir).withMapping(mapping),
      logger
    )
  } yield result

  def pathMapping(subdir: String): PathMapping =
    PathMapping((dir, id) => dir / subdir / id.id)(file =>
      Some(BinaryId(file.fileName.toString))
    )

  def isEmpty(dir: Path) =
    Files[IO].list(dir).head.compile.last.map(_.isEmpty)

  test("removeEmptyDirs until given root") {
    for {
      subdirs <- IO("a/b/c")
      remover <- createEmptyDirRemove(pathMapping(subdirs))
      _ <- Files[IO].createDirectories(remover.config.baseDir / subdirs)
      id <- BinaryId.random[IO]
      _ <- remover.removeEmptyDirs(id)
      _ <- assertIO(isEmpty(remover.config.baseDir), true)
    } yield ()
  }

  test("removeEmptyDirs concurrently on same file") {
    for {
      subdirs <- IO("a/b/c")
      remover <- createEmptyDirRemove(pathMapping(subdirs))
      _ <- Files[IO].createDirectories(remover.config.baseDir / subdirs)
      id <- BinaryId.random[IO]
      tasks = List.fill(8)(remover.removeEmptyDirs(id))
      _ <- tasks.parSequence
      _ <- assertIO(isEmpty(remover.config.baseDir), true)
    } yield ()
  }

  test("removeEmptyDirs until first non-empty") {
    for {
      subdirs <- IO("a/b/c")
      remover <- createEmptyDirRemove(pathMapping(subdirs))
      _ <- Files[IO].createDirectories(remover.config.baseDir / subdirs)
      _ <- Files[IO].createFile(remover.config.baseDir / "a" / "test.txt")
      id <- BinaryId.random[IO]
      _ <- remover.removeEmptyDirs(id)
      _ <- assertIO(Files[IO].exists(remover.config.baseDir / "a"), true)
      _ <- assertIO(isEmpty(remover.config.baseDir / "a"), false)
    } yield ()
  }
}
