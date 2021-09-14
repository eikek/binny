package binny.util

import cats.Applicative
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
}
