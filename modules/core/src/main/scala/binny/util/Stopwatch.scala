package binny.util

import cats.effect.Sync

import scala.concurrent.duration._
import cats.implicits._

object Stopwatch {

  type Watch[F[_]] = F[Duration]

  def start[F[_]: Sync]: F[Watch[F]] =
    Sync[F].delay {
      val started = System.nanoTime()
      Sync[F].delay(Duration.fromNanos(System.nanoTime() - started))
    }

  def printTime[F[_]: Sync](msg: String, w: Watch[F]): F[Unit] =
    Stopwatch(w)(d => Sync[F].delay(println(s"$msg ${humanTime(d)}")))

  def apply[F[_]: Sync](time: Watch[F])(f: Duration => F[Unit]): F[Unit] =
    time.flatMap(f)

  def show[F[_]: Sync](time: Watch[F])(f: String => F[Unit]): F[Unit] =
    apply(time)(d => f(humanTime(d)))

  def humanTime(d: Duration): String =
    d match {
      case fd: FiniteDuration =>
        val min = fd.toMinutes
        val sec = fd.minus(min.minutes).toSeconds
        val ms  = fd.minus(min.minutes).minus(sec.seconds).toMillis
        if (min > 0) f"$min%dmin $sec%02ds $ms%03dms"
        else f"$sec%ds $ms%03d ms"
      case d =>
        s"$d"
    }
}
