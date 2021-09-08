package binny

import cats.data._
import fs2.Stream
import cats.effect._
import cats.implicits._

trait BinaryStore[F[_]] {

  def insert(data: Stream[F, Byte])(implicit F: Sync[F]): F[BinaryId] =
    for {
      id <- BinaryId.random[F]
      _ <- insertWith(BinaryData(id, data))
    } yield id


  def insertWith(data: BinaryData[F]): F[Unit]

  def load(id: BinaryId): OptionT[F, BinaryData[F]]

  def delete(id: BinaryId): F[Boolean]

}

object BinaryStore {

  // type Save[F[_]] = Kleisli[F, Stream[F, Byte], BinaryId]

  // type SaveWith[F[_]] = Kleisli[F, BinaryData[F], Unit]

  // type Load[F[_]] = Kleisli[OptionT[F, *], BinaryId, BinaryData[F]]

}
