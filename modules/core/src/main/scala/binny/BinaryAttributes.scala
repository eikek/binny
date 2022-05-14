package binny

import java.io.{ByteArrayOutputStream, StringReader}
import java.security.MessageDigest
import java.util.Properties

import scala.util.Try

import fs2.{Chunk, Pipe, Stream}
import scodec.bits.ByteVector

/** Basic attributes of binary data. */
final case class BinaryAttributes(
    sha256: ByteVector,
    contentType: SimpleContentType,
    length: Long
)

object BinaryAttributes {
  val empty: BinaryAttributes =
    BinaryAttributes(ByteVector.empty, SimpleContentType.octetStream, 0L)

  def asString(ba: BinaryAttributes): String = {
    val props = new Properties
    props.setProperty("sha256", ba.sha256.toBase64)
    props.setProperty("contentType", ba.contentType.contentType)
    props.setProperty("length", ba.length.toString)
    val baos = new ByteArrayOutputStream
    props.store(baos, "binary attributes")
    baos.toString("ISO-8859-1")
  }

  def fromString(str: String): Either[String, BinaryAttributes] = {
    val props = new Properties
    for {
      _ <- Try(props.load(new StringReader(str))).toEither.left.map(_.getMessage)
      sha256 <- Option(props.getProperty("sha256"))
        .map(s => ByteVector.fromBase64Descriptive(s))
        .toRight(s"No sha256 property found in $str")
        .flatten
      ct <- Option(props.getProperty("contentType"))
        .map(SimpleContentType.apply)
        .toRight(s"No contentType property found in $str")
      len <- Option(props.getProperty("length"))
        .toRight(s"No length property found in $str")
        .flatMap(s => s.toLongOption.toRight(s"Invalid length value: $s"))
    } yield BinaryAttributes(sha256, ct, len)
  }

  def unsafeFromString(str: String): BinaryAttributes =
    fromString(str).fold(sys.error, identity)

  def compute[F[_]](ct: ContentTypeDetect, hint: Hint): Pipe[F, Byte, BinaryAttributes] =
    in =>
      Stream.suspend {
        in.chunks.fold(State.empty)(_.update(ct, hint)(_)).map(_.toAttributes)
      }

  final private class State(md: MessageDigest, len: Long, ct: Option[SimpleContentType]) {
    def update(detect: ContentTypeDetect, hint: Hint)(c: Chunk[Byte]): State = {
      md.update(c.toArraySlice.values)
      new State(md, len + c.size, ct.orElse(Some(detect.detect(c.toByteVector, hint))))
    }

    def toAttributes: BinaryAttributes =
      BinaryAttributes(
        ByteVector.view(md.digest()),
        ct.getOrElse(SimpleContentType.octetStream),
        len
      )
  }
  private object State {
    def empty = new State(MessageDigest.getInstance("SHA-256"), 0, None)
  }
}
