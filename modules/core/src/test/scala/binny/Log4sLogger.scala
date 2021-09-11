package binny

import binny.util.Logger
import cats.effect.kernel.Sync
import org.log4s.{Logger => SLogger}

object Log4sLogger {

  def apply[F[_]: Sync](log: SLogger): Logger[F] =
    new Logger[F] {
      override def trace(msg: => String): F[Unit] =
        Sync[F].delay(log.trace(msg))

      override def debug(msg: => String): F[Unit] =
        Sync[F].delay(log.debug(msg))

      override def info(msg: => String): F[Unit] =
        Sync[F].delay(log.info(msg))

      override def warn(msg: => String): F[Unit] =
        Sync[F].delay(log.warn(msg))

      override def error(msg: => String): F[Unit] =
        Sync[F].delay(log.error(msg))

      override def error(ex: Throwable)(msg: => String): F[Unit] =
        Sync[F].delay(log.error(ex)(msg))
    }
}
