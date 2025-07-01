package binny.stacked

import binny._
import binny.util.Logger
import cats.data.{NonEmptyList, OptionT}
import cats.effect._
import cats.implicits._
import fs2.{Pipe, Stream}

/** A utility to chain stores. The first store in the given list is the "main" store. It
  * will be used for all read operations. Write operations happen first on the main store
  * and if successful they are applied to all others.
  */
final class StackedBinaryStore[F[_]: Async] private (
    logger: Logger[F],
    stores: NonEmptyList[BinaryStore[F]],
    maxOpen: Int
) extends BinaryStore[F] {

  val main: BinaryStore[F] = stores.head

  def listIds(prefix: Option[String], chunkSize: Int): Stream[F, BinaryId] =
    main.listIds(prefix, chunkSize)

  private def withRemaining[A](f: BinaryStore[F] => Stream[F, A]): Stream[F, A] = {
    val all = Stream.emits(stores.tail).map(f)
    if (maxOpen > 1) all.parJoin(maxOpen)
    else all.flatten
  }

  def insert: Pipe[F, Byte, BinaryId] = in =>
    in
      .through(main.insert)
      .flatMap { id =>
        val rest = withRemaining(s => in.through(s.insertWith(id)))
        rest ++ Stream.emit(id)
      }

  def insertWith(id: BinaryId): Pipe[F, Byte, Nothing] =
    in => {
      val a = in.through(main.insertWith(id))
      val rest = withRemaining(s => in.through(s.insertWith(id)))
      a ++ rest
    }

  def findBinary(id: BinaryId, range: ByteRange): OptionT[F, Binary[F]] =
    main.findBinary(id, range)

  def exists(id: BinaryId): F[Boolean] =
    main.exists(id)

  def delete(id: BinaryId): F[Unit] =
    main.delete(id) *> withRemaining(s => Stream.eval(s.delete(id))).compile.drain

  def computeAttr(id: BinaryId, hint: Hint) =
    main.computeAttr(id, hint)

  def copyMainToRest: F[List[CopyTool.Counter]] = {
    val maxConcurrent = math.max(Runtime.getRuntime.availableProcessors() * 1.5, 4)
    def copyTo(to: BinaryStore[F]) =
      CopyTool.copyAll(logger, main, to, 100, maxConcurrent.toInt)

    stores.tail.traverse(copyTo)
  }
}

object StackedBinaryStore {
  def of[F[_]: Async](
      logger: Logger[F],
      main: BinaryStore[F],
      next: BinaryStore[F],
      rest: BinaryStore[F]*
  ): BinaryStore[F] =
    apply(logger, NonEmptyList(main, next :: rest.toList))

  def apply[F[_]: Async](
      logger: Logger[F],
      stack: NonEmptyList[BinaryStore[F]]
  ): BinaryStore[F] =
    stack.tail match {
      case Nil => stack.head
      case _   =>
        new StackedBinaryStore[F](
          logger,
          stack,
          math.min(Runtime.getRuntime.availableProcessors(), stack.tail.size)
        )
    }
}
