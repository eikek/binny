package binny

import cats.data.OptionT
import cats.effect.IO
import fs2.Stream

object SimpleBinaryStoreFixtures {
  val throwOnRead: BinaryStore[IO] =
    new BinaryStore[IO] {
      def listIds(prefix: Option[String], chunkSize: Int) =
        Stream.raiseError[IO](new RuntimeException("invalid"))
      def insert =
        _ => Stream.eval(BinaryId.random[IO])
      def insertWith(id: BinaryId) =
        _ => Stream.empty
      def findBinary(id: BinaryId, range: ByteRange) =
        OptionT.liftF(IO.raiseError(new RuntimeException("invalid")))
      def exists(id: BinaryId) =
        IO.raiseError(new RuntimeException("invalid"))
      def computeAttr(id: BinaryId, hint: Hint): ComputeAttr[IO] =
        ComputeAttr.raiseError[IO](new RuntimeException("invalid"))
      def delete(id: BinaryId) = IO.unit
    }

  val throwAlways: BinaryStore[IO] =
    new BinaryStore[IO] {
      def listIds(prefix: Option[String], chunkSize: Int) =
        Stream.raiseError[IO](new RuntimeException("invalid"))
      def insert =
        _ => Stream.raiseError[IO](new RuntimeException("invalid"))
      def insertWith(id: BinaryId) =
        _ => Stream.raiseError[IO](new RuntimeException("invalid"))
      def findBinary(id: BinaryId, range: ByteRange) =
        OptionT.liftF(IO.raiseError(new RuntimeException("invalid")))
      def exists(id: BinaryId) =
        IO.raiseError(new RuntimeException("invalid"))
      def computeAttr(id: BinaryId, hint: Hint) =
        ComputeAttr.raiseError[IO](new RuntimeException("invalid"))
      def delete(id: BinaryId) =
        IO.raiseError(new RuntimeException("invalid"))
    }
}
