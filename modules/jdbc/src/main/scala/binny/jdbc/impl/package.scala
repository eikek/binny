package binny.jdbc

import java.sql.{Connection, PreparedStatement, ResultSet}
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource

import binny.util.Logger
import cats.Applicative
import cats.data.Kleisli
import cats.effect._
import cats.implicits._

package object impl {
  type DbRun[F[_], A] = Kleisli[F, Connection, A]
  type DbRunIO[A]     = DbRun[IO, A]

  object DbRun {
    implicit private[impl] def logger[F[_]: Sync]: Logger[F] = Logger.silent[F]
    private[this] val counter                                = new AtomicLong(0)

    def apply[F[_], A](f: Connection => F[A]): DbRun[F, A] =
      Kleisli(f)

    def pure[F[_]: Applicative, A](a: A): DbRun[F, A] =
      apply(_ => Applicative[F].pure(a))

    def delay[F[_]: Sync, A](f: Connection => A): DbRun[F, A] =
      DbRun(conn => Sync[F].blocking(f(conn)))

    def fail[F[_]: Sync, A](ex: Throwable): DbRun[F, A] =
      DbRun(_ => Sync[F].raiseError(ex))

    def resource[F[_]: Sync, A](
        acquire: Connection => A
    )(release: A => Unit): DbRun[Resource[F, *], A] =
      DbRun(conn =>
        Resource.make(Sync[F].blocking(acquire(conn)))(a => Sync[F].blocking(release(a)))
      )

    def setAutoCommit[F[_]: Sync](flag: Boolean): DbRun[F, Boolean] =
      delay { conn =>
        val old = conn.getAutoCommit
        conn.setAutoCommit(flag)
        old
      }

    def commit[F[_]: Sync]: DbRun[F, Unit] =
      delay(_.commit())

    def rollback[F[_]: Sync]: DbRun[F, Unit] =
      delay(_.rollback())

    def makeTX[F[_]: Sync](implicit log: Logger[F]): DbRun[Resource[F, *], Unit] =
      DbRun { conn =>
        val autoCommit =
          Resource.make(for {
            c  <- Sync[F].delay(counter.getAndIncrement())
            _  <- log.trace(s"Initiating transaction $c")
            ac <- setAutoCommit(false).run(conn)
          } yield ac)(ac => setAutoCommit(ac).run(conn).map(_ => ()))

        val lastCommit =
          Resource.makeCase(Sync[F].pure(())) {
            case (_, Resource.ExitCase.Errored(ex)) =>
              log.error(ex)(s"Error during transaction!") *>
                rollback.run(conn)

            case (_, Resource.ExitCase.Canceled) =>
              log.info(s"Transaction cancelled!") *>
                rollback.run(conn)

            case (_, Resource.ExitCase.Succeeded) =>
              log.trace(s"Transaction successful!") *>
                commit.run(conn)
          }

        autoCommit.flatMap(_ => lastCommit)
      }

    def inTX[F[_]: Sync, A](dbr: DbRun[F, A])(implicit log: Logger[F]): DbRun[F, A] =
      withResourceIn(makeTX[F])(_ => dbr)

    def withResource[F[_]: Sync, A, B](dba: DbRun[Resource[F, *], A])(
        f: A => F[B]
    ): DbRun[F, B] =
      dba.mapF(_.use(f))

    def withResourceIn[F[_]: Sync, A, B](dba: DbRun[Resource[F, *], A])(
        f: A => DbRun[F, B]
    ): DbRun[F, B] =
      DbRun(conn => dba.mapF(_.use(a => f(a).run(conn))).run(conn))

    def prepare[F[_]: Sync](
        sql: String
    )(implicit log: Logger[F]): DbRun[Resource[F, *], PreparedStatement] =
      DbRun(_ => Resource.eval(log.trace(s"Prepare statement: $sql"))) *>
        DbRun.resource(_.prepareStatement(sql))(_.close())

    def executeQuery[F[_]: Sync](
        ps: PreparedStatement
    ): DbRun[Resource[F, *], ResultSet] =
      DbRun.resource(_ => ps.executeQuery())(_.close())

    def executeUpdate[F[_]: Sync](ps: PreparedStatement): F[Int] =
      Sync[F].blocking(ps.executeUpdate())

    def executeUpdate[F[_]: Sync](sql: String)(implicit log: Logger[F]): DbRun[F, Int] =
      DbRun(_ => log.trace(s"Execute update: $sql")) *>
        delay(_.createStatement().executeUpdate(sql))

    def query[F[_]: Sync](
        sql: String
    )(
        set: PreparedStatement => Unit
    )(implicit log: Logger[F]): DbRun[Resource[F, *], ResultSet] =
      for {
        ps <- prepare(sql)
        _  <- DbRun(_ => Resource.eval(Sync[F].delay(set(ps))))
        rs <- executeQuery(ps)
      } yield rs

    def hasNext[F[_]: Sync](rs: ResultSet): F[Boolean] =
      Sync[F].blocking(rs.next())

    def readOpt[F[_]: Sync, A](f: ResultSet => A)(rs: ResultSet): F[Option[A]] =
      Sync[F].blocking(if (rs.next()) Some(f(rs)) else None)

    def update[F[_]: Sync](
        sql: String
    )(set: PreparedStatement => Unit)(implicit log: Logger[F]): DbRun[F, Int] =
      withResource(prepare(sql))(ps =>
        Sync[F].blocking {
          set(ps)
          ps.executeUpdate()
        }
      )
  }

  object DataSourceResource {
    def apply[F[_]: Sync](ds: DataSource): Resource[F, Connection] =
      Resource.make(Sync[F].blocking(ds.getConnection))(conn =>
        Sync[F].blocking(conn.close())
      )
  }
}
