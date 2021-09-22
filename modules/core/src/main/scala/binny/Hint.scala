package binny

final case class Hint(filename: Option[String], advertisedType: Option[String]) {
  def withAdvertised(contentType: String): Hint =
    copy(advertisedType = Option(contentType).filter(_.nonEmpty))

  def withFilename(filename: String): Hint =
    copy(filename = Option(filename).filter(_.nonEmpty))
}

object Hint {
  val none = Hint(None, None)

  def filename(fn: String): Hint =
    none.withFilename(fn)

  def advertised(contentType: String): Hint =
    none.withAdvertised(contentType)

  def apply(fn: String, advertised: String): Hint =
    filename(fn).withAdvertised(advertised)
}
