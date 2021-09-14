package binny

import binny.ContentTypeDetect.Hint
import cats.effect._
import Binary.Implicits._
import scodec.bits.ByteVector

object ExampleData {

  def helloWorld[F[_]]: Binary[F] =
    Binary.utf8String("Hello World!")

  def file2M: Binary[IO] =
    fs2.io.readInputStream(
      IO(getClass.getResource("/file_2M.txt").openStream()),
      64 * 1024
    )

  val file2MAttr: BinaryAttributes =
    BinaryAttributes(ByteVector.fromValidHex("f69322b62de9c0196cf858a8c023a0cb21171fcfc34bb757137bfbf9953a4b2e"),
      SimpleContentType.octetStream,1978876L)

  def helloWorldAttr: IO[BinaryAttributes] =
    helloWorld[IO].computeAttributes(ContentTypeDetect.none, Hint.none).compile.lastOrError
}
