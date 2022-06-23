package binny

import java.io.{ByteArrayOutputStream, StringReader}
import java.nio.charset.StandardCharsets
import java.util.Properties

import scala.util.Try

import scodec.bits.ByteVector

/** Basic attributes of binary data. */
final case class BinaryAttributes(
    sha256: ByteVector,
    contentType: SimpleContentType,
    length: Long
)

object BinaryAttributes {
  val empty: BinaryAttributes =
    BinaryAttributes(ByteVector.empty, SimpleContentType.octetStream, -1L)

  def apply(contentType: SimpleContentType, length: Long): BinaryAttributes =
    BinaryAttributes(ByteVector.empty, contentType, length)

  def asString(ba: BinaryAttributes): String = {
    val props = new Properties()
    props.setProperty("sha256", ba.sha256.toBase64)
    props.setProperty("contentType", ba.contentType.contentType)
    props.setProperty("length", ba.length.toString)
    val baos = new ByteArrayOutputStream()
    props.store(baos, "binary attributes")
    baos.toString(StandardCharsets.UTF_8)
  }

  def fromString(str: String): Either[String, BinaryAttributes] = {
    val props = new Properties()
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
}
