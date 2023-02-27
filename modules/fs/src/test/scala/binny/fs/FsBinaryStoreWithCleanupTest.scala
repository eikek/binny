package binny.fs

import binny.BinaryId
import binny.util.Logger
import cats.effect.IO
import cats.syntax.all._
import fs2.Stream
import fs2.io.file.{Files, Path}
import munit.CatsEffectSuite

class FsBinaryStoreWithCleanupTest extends CatsEffectSuite {
  implicit val logger: Logger[IO] = Logger.silent[IO]

  val testDirectory = ResourceSuiteLocalFixture[Path](
    "test-directory",
    Files[IO].tempDirectory(Some(Path("target")), "fs-with-cleanup-", None)
  )

  override def munitFixtures = List(testDirectory)

  def createTestDirectory = for {
    testDir <- IO(testDirectory())
    baseDir = testDir / "%.0f".format(math.random() * 1_000_000)
    _ <- Files[IO].createDirectories(baseDir)
  } yield baseDir

  def createStore(baseDir: Path): IO[FsBinaryStoreWithCleanup[IO]] = {
    val cfg = FsStoreConfig
      .default(baseDir)
      .withMapping(PathMapping.subdir2("file"))
    FsBinaryStoreWithCleanup[IO](cfg)
  }

  def createSamePrefixIds(
      n: Int,
      prefixes: List[String]
  ): List[BinaryId] =
    List
      .range(0, n)
      .map(d => prefixes(d % prefixes.length) + d.toString)
      .map(BinaryId(_))

  val testData = Stream.emit("hello").through(fs2.text.utf8.encode).covary[IO]

  test("insert and delete concurrently") {
    for {
      // prepare
      baseDir <- createTestDirectory
      store <- createStore(baseDir)

      // generate ids, half insert / half delete
      ids <- IO(createSamePrefixIds(20, prefixes = List("abc", "def")))
      (insIds, deleteIds) = ids.splitAt(ids.length / 2)
      inserts = Stream
        .emits(insIds)
        .covary[IO]
        .map(store.insertWith)
        .map(_.apply(testData))
      delete = Stream
        .emits(deleteIds)
        .covary[IO]
        .map(id => Stream.eval(store.delete(id)))

      // state invariant
      cond = store.state.continuous.evalMap { state =>
        val ok = state.noDeleteRunning || state.noInsertRunning
        if (!ok)
          IO.raiseError(
            new Exception(s"Invalid state during operation: $state")
          )
        else IO.pure(ok)
      }.drain

      // run all
      _ <- cond
        .mergeHaltR(inserts.interleave(delete).parJoinUnbounded.drain)
        .compile
        .drain

      // assertions
      // insert files must exist, deletion files+parent must not exist
      insExists <- insIds
        .map(id => store.underlying.config.targetFile(id))
        .traverse(Files[IO].exists)
      _ = assert(insExists.forall(_ == true))

      delExists <- deleteIds
        .map(id => store.underlying.config.targetFile(id))
        .map(_.parent.getOrElse(sys.error("No parent directory!")))
        .traverse(Files[IO].exists)
      _ = assert(delExists.forall(_ == false))

      state <- store.getState
      _ = assert(state.noInsertRunning && state.noDeleteRunning)
    } yield ()
  }
}
