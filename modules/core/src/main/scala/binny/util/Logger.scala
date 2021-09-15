package binny.util

import java.io.PrintStream
import java.time.Instant

import cats.Applicative
import cats.effect._
import cats.implicits._
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
  def silent[F[_]: Applicative]: Logger[F] =
    new Logger[F] {
      def trace(msg: => String): F[Unit] = Applicative[F].unit
      def debug(msg: => String): F[Unit] = Applicative[F].unit
      def info(msg: => String): F[Unit] = Applicative[F].unit
      def warn(msg: => String): F[Unit] = Applicative[F].unit
      def error(msg: => String): F[Unit] = Applicative[F].unit
      def error(ex: Throwable)(msg: => String): F[Unit] = Applicative[F].unit
    }

  def stdout[F[_]: Sync](verbosity: Int = 2, name: String = "binny"): Logger[F] =
    printStream(Console.out, verbosity, name)

  /** Obviously not for serious logging, but provides a quick output without any further
    * work. The `verbosity` controls the levels to log; 0 = off, 1 = ERROR, 2 = WARN etc.
    */
  def printStream[F[_]: Sync](ps: PrintStream, verbosity: Int, name: String): Logger[F] =
    new Logger[F] {
      val time: F[Instant] = Sync[F].delay(Instant.now())
      val thread: F[String] = Sync[F].delay(Thread.currentThread().getName)

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
          _ <- Sync[F].delay(ex.printStackTrace(ps))
        } yield ()
    }
}
