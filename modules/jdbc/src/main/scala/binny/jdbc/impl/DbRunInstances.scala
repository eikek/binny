package binny.jdbc.impl

import cats.Functor
import cats.effect._
import cats.effect.kernel.CancelScope

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.Try

trait DbRunInstances {
  implicit val monadInstance: Sync[DbRun] =
    new Sync[DbRun] {
      override def suspend[A](hint: Sync.Type)(thunk: => A): DbRun[A] =
        DbRun.defer(thunk)

      override def monotonic: DbRun[FiniteDuration] =
        DbRun.defer(Duration.fromNanos(System.nanoTime()))

      override def realTime: DbRun[FiniteDuration] =
        DbRun.defer(Duration.apply(System.currentTimeMillis(), TimeUnit.MILLISECONDS))

      def flatMap[A, B](fa: DbRun[A])(f: A => DbRun[B]): DbRun[B] =
        DbRun { conn =>
          val a = fa(conn)
          f(a)(conn)
        }

      def tailRecM[A, B](a: A)(f: A => DbRun[Either[A, B]]): DbRun[B] =
        DbRun { conn =>
          @annotation.tailrec
          def go(in: A): B =
            f(in)(conn) match {
              case Right(b) => b
              case Left(aa) => go(aa)
            }

          go(a)
        }

      def pure[A](x: A): DbRun[A] =
        DbRun.pure(x)

      def raiseError[A](e: Throwable): DbRun[A] =
        DbRun(_ => throw e)

      def handleErrorWith[A](fa: DbRun[A])(f: Throwable => DbRun[A]): DbRun[A] =
        DbRun { conn =>
          Try(fa(conn)).toEither match {
            case Right(a) => a
            case Left(ex) => f(ex)(conn)
          }
        }

      def rootCancelScope: CancelScope =
        CancelScope.Uncancelable

      override def forceR[A, B](fa: DbRun[A])(fb: DbRun[B]): DbRun[B] =
        fb

      def uncancelable[A](body: Poll[DbRun] => DbRun[A]): DbRun[A] =
        body(IdPoll)

      def canceled: DbRun[Unit] =
        DbRun.pure(())

      def onCancel[A](fa: DbRun[A], fin: DbRun[Unit]): DbRun[A] =
        flatMap(fa)(a => Functor[DbRun].map(fin)(_ => a))
    }

  private[this] val IdPoll = new Poll[DbRun] {
    def apply[A](fa: DbRun[A]) = fa
  }

  implicit val functorInstance: Functor[DbRun] =
    new Functor[DbRun] {
      def map[A, B](fa: DbRun[A])(f: A => B): DbRun[B] =
        DbRun(conn => f(fa(conn)))
    }
}
