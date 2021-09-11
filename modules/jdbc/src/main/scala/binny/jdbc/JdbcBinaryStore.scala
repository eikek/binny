package binny.jdbc

import javax.sql.DataSource
import binny._
import binny.jdbc.impl.DbRun
import cats.data.OptionT
import cats.effect._
import DbRun._
import cats.implicits._
import fs2.Stream

final class JdbcBinaryStore[F[_]: Async](ds: DataSource, val config: JdbcStoreConfig)
    extends BinaryStore[F] {

  def runSetup(dbms: Dbms): F[Int] =
    DatabaseSetup.run[F](dbms, ds, config)

  def insertWith(data: BinaryData[F], hint: ContentTypeDetect.Hint): F[Unit] = {
    val attrInit = config.metaTable match {
      case Some(at) =>
        DbRun.insertEmptyAttr(at, data.id)
      case None =>
        DbRun.pure(0)
    }
    val inserts =
      DbRun
        .insertAllData(config.dataTable, data)
        .evalMap(_.inTX.execute[F](ds))
        .compile
        .foldMonoid

    val saveAttr = config.metaTable match {
      case Some(at) =>
        val ba =
          dataStream(data.id)
            .through(BinaryAttributes.compute(config.detect, hint))
            .compile
            .lastOrError
        ba.map(DbRun.updateAttr(at, data.id, _))
          .flatMap(_.execute[F](ds))
      case None =>
        0.pure[F]
    }

    for {
      _ <- attrInit.inTX.execute[F](ds)
      _ <- inserts
      _ <- saveAttr
    } yield ()
  }

  def delete(id: BinaryId): F[Boolean] =
    for {
      n <- DbRun.deleteFrom(config.dataTable, id).inTX.execute[F](ds)
      _ <- config.metaTable match {
        case Some(at) =>
          DbRun.deleteFrom(at, id).inTX.execute[F](ds)
        case None =>
          0.pure[F]
      }
    } yield n > 0

  private def dataStream(id: BinaryId) =
    Stream
      .iterate(0)(_ + 1)
      .map(n => queryChunk(config.dataTable, id, n))
      .covary[F]
      .evalMap(_.execute[F](ds))
      .unNoneTerminate
      .flatMap(Stream.chunk)

  def load(id: BinaryId, range: ByteRange, chunkSize: Int): OptionT[F, BinaryData[F]] = {
    val data = dataStream(id)
    OptionT(
      DbRun
        .exists(config.dataTable, id)
        .execute[F](ds)
        .map(exists => if (exists) Some(BinaryData[F](id, data)) else None)
    )
  }
}

object JdbcBinaryStore {

  def apply[F[_]: Async](ds: DataSource, config: JdbcStoreConfig): JdbcBinaryStore[F] =
    new JdbcBinaryStore[F](ds, config)
}
