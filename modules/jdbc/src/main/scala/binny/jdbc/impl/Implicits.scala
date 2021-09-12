package binny.jdbc.impl

import javax.sql.DataSource

import binny.util.Logger
import cats.Monad
import cats.effect._
import cats.implicits._
import cats.kernel.Monoid

trait Implicits {

  implicit def dbRunMonoid[F[_]: Monad, A: Monoid]: Monoid[DbRun[F, A]] =
    Monoid.instance(
      DbRun.pure(Monoid[A].empty),
      (r1, r2) => r1.flatMap(a1 => r2.map(a2 => Monoid[A].combine(a1, a2)))
    )

  implicit final class DbRunIOOps[F[_]: Sync, A](dbio: DbRun[F, A]) {
    def execute(ds: DataSource): F[A] =
      DataSourceResource(ds).use(dbio.run)

    def inTX(implicit log: Logger[F]): DbRun[F, A] =
      DbRun.inTX(dbio)

    def attempt: DbRun[F, Either[Throwable, A]] =
      dbio.mapF(_.attempt)
  }

  implicit final class DbRunIOResourceOps[F[_], A](rio: DbRun[Resource[F, *], A]) {
    def use[B](f: A => F[B])(implicit F: Sync[F]): DbRun[F, B] =
      DbRun.withResource(rio)(f)

    def useIn[B](f: A => DbRun[F, B])(implicit F: Sync[F]): DbRun[F, B] =
      DbRun.withResourceIn(rio)(f)
  }

}

object Implicits extends Implicits
