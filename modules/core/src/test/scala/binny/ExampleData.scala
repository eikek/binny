package binny

import cats.effect._
import fs2.Stream
import scodec.bits.ByteVector

object ExampleData {

  def helloWorld[F[_]]: Binary[F] =
    Binary.utf8String("Hello World!")

  def file2M: Binary[IO] =
    fs2.io.readInputStream(
      IO(getClass.getResource("/file_2M.txt").openStream()),
      64 * 1024
    )

  def logoPng: Binary[IO] =
    fs2.io.readInputStream(
      IO(getClass.getResource("/logo.png").openStream()),
      64 * 1024
    )

  val file2MAttr: BinaryAttributes =
    BinaryAttributes(
      ByteVector.fromValidHex(
        "f69322b62de9c0196cf858a8c023a0cb21171fcfc34bb757137bfbf9953a4b2e"
      ),
      SimpleContentType.octetStream,
      1978876L
    )

  def helloWorldAttr: IO[BinaryAttributes] =
    helloWorld[IO]
      .computeAttributes(ContentTypeDetect.none, Hint.none)
      .compile
      .lastOrError

  def fail: Binary[IO] =
    file2M ++ binaryFail

  private def binaryFail: Binary[IO] =
    Stream.eval(IO(sys.error("error!!")))

  implicit class StreamExtras(self: Stream[IO, Byte]) {
    def computeAttributes(detect: ContentTypeDetect, hint: Hint) =
      self.through(ComputeAttr.computeAll(detect, hint))

    def readUtf8String =
      self.through(fs2.text.utf8.decode).compile.string

  }
}
