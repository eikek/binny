package binny

import binny.ContentTypeDetect.Hint
import cats.effect._
import cats.implicits._
import fs2.Stream

trait BinaryStore[F[_]] extends ReadonlyStore[F] {

  def insert(data: Stream[F, Byte], hint: Hint)(implicit F: Sync[F]): F[BinaryId] =
    for {
      id <- BinaryId.random[F]
      _  <- insertWith(BinaryData(id, data), hint)
    } yield id

  def insertWith(data: BinaryData[F], hint: Hint): F[Unit]

  def delete(id: BinaryId): F[Boolean]

}

object BinaryStore {}
