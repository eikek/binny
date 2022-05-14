package binny.jdbc.impl

import javax.sql.DataSource

import binny.util.Logger
import cats._
import cats.effect._
import cats.implicits._

object Implicits {
  implicit def monoidInstance[F[_]: Monad, A: Monoid]: Monoid[DbRun[F, A]] =
    Monoid.instance(
      DbRun.pure(Monoid[A].empty),
      (r1, r2) => r1.flatMap(a1 => r2.map(a2 => Monoid[A].combine(a1, a2)))
    )

  implicit final class DbRunSyncOps[F[_]: Sync, A](private val self: DbRun[F, A]) {
    def execute(ds: DataSource): F[A] =
      DataSourceResource(ds).use(self.run)

    def inTX(implicit log: Logger[F]): DbRun[F, A] =
      DbRun.inTX(self)

    def attempt: DbRun[F, Either[Throwable, A]] =
      self.mapF(_.attempt)
  }

  implicit final class DbRunIOResourceOps[F[_], A](
      private val self: DbRun[Resource[F, *], A]
  ) {
    def use[B](f: A => F[B])(implicit F: Sync[F]): DbRun[F, B] =
      DbRun.withResource(self)(f)

    def useIn[B](f: A => DbRun[F, B])(implicit F: Sync[F]): DbRun[F, B] =
      DbRun.withResourceIn(self)(f)
  }

  implicit def functionKInstance[F[_]]: F ~> DbRun[F, *] =
    new F ~> DbRun[F, *] {
      def apply[X](fx: F[X]) = DbRun(_ => fx)
    }
}
