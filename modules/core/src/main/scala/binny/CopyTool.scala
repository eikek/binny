package binny

import binny.util.{Logger, Stopwatch}
import cats.Monoid
import cats.effect.{Async, Sync}
import cats.implicits._

object CopyTool {

  final case class Counter(success: Int, exist: Int, notFound: Int, failed: Set[BinaryId])

  object Counter {
    val zero: Counter = Counter(0, 0, 0, Set.empty)

    val success: Counter = zero.copy(success = 1)
    val existing: Counter = zero.copy(exist = 1)
    val notFound: Counter = zero.copy(notFound = 1)
    def failed(id: BinaryId): Counter = zero.copy(failed = Set(id))

    implicit val counterMonoid: Monoid[Counter] =
      Monoid.instance(
        zero,
        (a, b) =>
          Counter(
            a.success + b.success,
            a.exist + b.exist,
            a.notFound + b.notFound,
            a.failed ++ b.failed
          )
      )
  }

  /** Goes through all binary-ids in `from` and inserts each into `to`. */
  def copyAll[F[_]: Async](
      logger: Logger[F],
      from: BinaryStore[F],
      to: BinaryStore[F],
      chunkSize: Int,
      maxConcurrent: Int
  ): F[Counter] = {
    val allIds =
      from
        .listIds(None, chunkSize)
        .chunkN(chunkSize)
        .evalTap(ch => logger.info(s"Copying ${ch.size} files …"))
        .unchunks

    val copied =
      if (maxConcurrent > 1)
        allIds
          .parEvalMap(maxConcurrent)(id => copyFile(id, from, to, logger))
      else
        allIds
          .evalMap(id => copyFile(id, from, to, logger))

    for {
      w <- Stopwatch.start[F]
      c <- copied.compile.foldMonoid
      _ <- Stopwatch.show(w)(d =>
        logger.info(
          s"Copying ${c.success} files and ${c.exist} existing (by par=$maxConcurrent) took $d"
        )
      )
    } yield c
  }

  /** Copies the file given by `id` from `from` to `to`, if it doesn't exists there. */
  def copyFile[F[_]: Sync](
      id: BinaryId,
      from: BinaryStore[F],
      to: BinaryStore[F],
      logger: Logger[F]
  ): F[Counter] =
    to.exists(id).flatMap {
      case true =>
        logger.trace(s"Binary $id already exists.").as(Counter.existing)
      case false =>
        from
          .findBinary(id, ByteRange.All)
          .semiflatMap(data =>
            logger.trace(s"Copying ${id.id} …") *>
              data.through(to.insertWith(id)).compile.drain.attempt.flatMap {
                case Right(()) => Counter.success.pure[F]
                case Left(ex) =>
                  logger
                    .error(ex)(s"Error storing file '$id'")
                    .as(Counter.failed(id))
              }
          )
          .getOrElse(Counter.notFound)
    }
}
