package binny

import binny.ContentTypeDetect.Hint
import cats.Applicative
import cats.data.OptionT
import cats.effect._
import cats.implicits._
import fs2.{Pipe, Stream}

trait BinaryStore[F[_]] extends ReadonlyStore[F] with ReadonlyAttributeStore[F] {

  def insert(data: Stream[F, Byte], hint: Hint)(implicit F: Sync[F]): F[BinaryId] =
    for {
      id <- BinaryId.random[F]
      _ <- insertWith(BinaryData(id, data), hint)
    } yield id

  def insertWith(data: BinaryData[F], hint: Hint): F[Unit]

  def insertTo(id: BinaryId, hint: Hint): Pipe[F, Byte, Nothing] =
    in => Stream.eval(insertWith(BinaryData(id, in), hint)).drain

  def delete(id: BinaryId): F[Boolean]

}

object BinaryStore {

  def none[F[_]: Applicative]: BinaryStore[F] =
    new BinaryStore[F] {
      def insertWith(data: BinaryData[F], hint: Hint): F[Unit] =
        ().pure[F]

      def delete(id: BinaryId): F[Boolean] =
        false.pure[F]

      def findBinary(id: BinaryId, range: ByteRange): OptionT[F, BinaryData[F]] =
        OptionT.none

      def findAttr(id: BinaryId): OptionT[F, BinaryAttributes] =
        OptionT.none
    }

}
