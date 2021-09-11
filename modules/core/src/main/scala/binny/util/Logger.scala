package binny.util

import cats.Applicative

/** To not force anyone to a specific logging api, this facade is used in this lib for logging. */
trait Logger[F[_]] {

  def trace(msg: => String): F[Unit]

  def debug(msg: => String): F[Unit]

  def info(msg: => String): F[Unit]

  def warn(msg: => String): F[Unit]

  def error(msg: => String): F[Unit]

  def error(ex: Throwable)(msg: => String): F[Unit]
}

object Logger {
  def silent[F[_]: Applicative]: Logger[F] =
    new Logger[F] {
      def trace(msg: => String): F[Unit]                = Applicative[F].unit
      def debug(msg: => String): F[Unit]                = Applicative[F].unit
      def info(msg: => String): F[Unit]                 = Applicative[F].unit
      def warn(msg: => String): F[Unit]                 = Applicative[F].unit
      def error(msg: => String): F[Unit]                = Applicative[F].unit
      def error(ex: Throwable)(msg: => String): F[Unit] = Applicative[F].unit
    }
}
