package binny

/** A content type as a String. A proper model for content types is beyond the scope of
  * this library
  */
final class SimpleContentType private (val contentType: String) extends AnyVal {

  def or(other: => SimpleContentType): SimpleContentType =
    if (contentType.startsWith(SimpleContentType.octetStream.contentType)) other
    else this

  def isText: Boolean =
    contentType.startsWith("text/")

  override def toString(): String = s"SimpleContentType($contentType)"
}

object SimpleContentType {
  val octetStream: SimpleContentType =
    new SimpleContentType("application/octet-stream")

  def apply(ct: String): SimpleContentType =
    if (ct.trim.isEmpty) octetStream
    else new SimpleContentType(ct)
}
