package binny.util

import java.io.PrintStream
import java.time.Instant

import cats.effect._
import cats.implicits._
import cats.{Applicative, Order}
import fs2.Stream

/** To not force anyone to a specific logging api, this facade is used in this lib for
  * logging.
  */
trait Logger[F[_]] { self =>

  def trace(msg: => String): F[Unit]

  def debug(msg: => String): F[Unit]

  def info(msg: => String): F[Unit]

  def warn(msg: => String): F[Unit]

  def error(msg: => String): F[Unit]

  def error(ex: Throwable)(msg: => String): F[Unit]

  final def s: Logger[Stream[F, *]] = new Logger[Stream[F, *]] {
    def trace(msg: => String): Stream[F, Unit] =
      Stream.eval(self.trace(msg))

    def debug(msg: => String): Stream[F, Unit] =
      Stream.eval(self.debug(msg))

    def info(msg: => String): Stream[F, Unit] =
      Stream.eval(self.info(msg))

    def warn(msg: => String): Stream[F, Unit] =
      Stream.eval(self.warn(msg))

    def error(msg: => String): Stream[F, Unit] =
      Stream.eval(self.error(msg))

    def error(ex: Throwable)(msg: => String): Stream[F, Unit] =
      Stream.eval(self.error(ex)(msg))
  }
}

object Logger {
  def apply[F[_]](implicit ev: Logger[F]): Logger[F] = ev

  sealed trait Level {
    def asInt: Int
  }
  object Level {
    case object Trace extends Level {
      val asInt = 5
    }
    case object Debug extends Level {
      val asInt = 4
    }
    case object Info extends Level {
      val asInt = 3
    }
    case object Warn extends Level {
      val asInt = 2
    }
    case object Error extends Level {
      val asInt = 1
    }
    case object Off extends Level {
      val asInt = 0
    }

    implicit val order: Order[Level] =
      Order.by(_.asInt)
  }

  def silent[F[_]: Applicative]: Logger[F] =
    new Logger[F] {
      def trace(msg: => String): F[Unit] = Applicative[F].unit
      def debug(msg: => String): F[Unit] = Applicative[F].unit
      def info(msg: => String): F[Unit] = Applicative[F].unit
      def warn(msg: => String): F[Unit] = Applicative[F].unit
      def error(msg: => String): F[Unit] = Applicative[F].unit
      def error(ex: Throwable)(msg: => String): F[Unit] = Applicative[F].unit
    }

  def stdout[F[_]: Sync](level: Level = Level.Warn, name: String = "binny"): Logger[F] =
    printStream(Console.out, level, name)

  /** Obviously not for serious logging, but provides a quick output without any further
    * work or dependencies. The `level` controls the levels to log.
    */
  def printStream[F[_]: Sync](ps: PrintStream, level: Level, name: String): Logger[F] =
    new Logger[F] {
      val time: F[Instant] = Sync[F].delay(Instant.now())
      val thread: F[String] = Sync[F].delay(Thread.currentThread().getName)
      val verbosity = level.asInt
      def log(level: String, msg: F[String]): F[Unit] =
        for {
          ts <- time
          tn <- thread
          m <- msg
          _ <- Sync[F].delay(ps.println(s"$ts [$tn] $level $name - $m"))
          _ <- Sync[F].delay(ps.flush())
        } yield ()

      def trace(msg: => String): F[Unit] =
        if (verbosity < 5) ().pure[F]
        else log("TRACE", Sync[F].delay(msg))

      def debug(msg: => String): F[Unit] =
        if (verbosity < 4) ().pure[F]
        else log("DEBUG", Sync[F].delay(msg))

      def info(msg: => String): F[Unit] =
        if (verbosity < 3) ().pure[F]
        else log("INFO", Sync[F].delay(msg))

      def warn(msg: => String): F[Unit] =
        if (verbosity < 2) ().pure[F]
        else log("WARN", Sync[F].delay(msg))

      def error(msg: => String): F[Unit] =
        if (verbosity < 1) ().pure[F]
        else log("ERROR", Sync[F].delay(msg))

      def error(ex: Throwable)(msg: => String): F[Unit] =
        for {
          _ <- error(msg)
          _ <- if (verbosity < 1) ().pure[F] else Sync[F].delay(ex.printStackTrace(ps))
        } yield ()
    }
}
