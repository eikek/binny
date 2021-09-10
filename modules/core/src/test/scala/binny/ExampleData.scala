package binny

import binny.ContentTypeDetect.Hint
import cats.effect.IO
import fs2.{Chunk, Stream}

object ExampleData {

  def empty[F[_]]: Stream[F, Byte] = Stream.empty.covary[F]

  def helloWorld[F[_]]: Stream[F, Byte] =
    Stream.chunk(Chunk.array("Hello World!".getBytes))

  def emptyData[F[_]]: BinaryData[F] =
    BinaryData(BinaryId("empty"), empty)

  def helloWorldData[F[_]]: BinaryData[F] =
    BinaryData(BinaryId("hello-world"), helloWorld)

  def helloWorldAttr: IO[BinaryAttributes] =
    helloWorldData[IO].computeAttributes(ContentTypeDetect.none, Hint.none)
}
