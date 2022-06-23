package binny

/** A content type as a String. A proper model for content types is beyond the scope of
  * this library
  */
final class SimpleContentType private (val contentType: String) extends AnyVal {

  def or(other: => SimpleContentType): SimpleContentType =
    if (isOctetStream) other else this

  def isOctetStream =
    contentType.startsWith(SimpleContentType.octetStream.contentType)

  def isText: Boolean =
    contentType.startsWith("text/")

  override def toString: String = s"SimpleContentType($contentType)"
}

object SimpleContentType {
  val octetStream: SimpleContentType =
    new SimpleContentType("application/octet-stream")

  val textPlain: SimpleContentType =
    new SimpleContentType("text/plain")

  def apply(ct: String): SimpleContentType =
    if (ct.trim.isEmpty) octetStream
    else new SimpleContentType(ct)
}
