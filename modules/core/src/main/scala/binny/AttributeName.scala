package binny

import cats.Order
import cats.data.NonEmptySet

sealed trait AttributeName {
  def name: String
}

object AttributeName {
  case object ContentType extends AttributeName {
    val name = productPrefix.toLowerCase
  }
  case object Length extends AttributeName {
    val name = productPrefix.toLowerCase
  }
  case object Sha256 extends AttributeName {
    val name = productPrefix.toLowerCase
  }

  implicit val order: Order[AttributeName] =
    Order.by(_.name)

  val lengthOnly: AttributeNameSet =
    select(Length)

  val contentTypeOnly: AttributeNameSet =
    select(ContentType)

  val sha256Only: AttributeNameSet =
    select(Sha256)

  val all: AttributeNameSet =
    select(Length, Sha256, ContentType)

  val excludeSha256: AttributeNameSet =
    select(Length, ContentType)

  def select(ba: AttributeName, bas: AttributeName*): AttributeNameSet =
    NonEmptySet.of(ba, bas: _*)

  object ContainsSha256 {
    def unapply(set: AttributeNameSet): Option[Sha256.type] =
      Some(Sha256).filter(set.contains)
  }
  object ContainsLength {
    def unapply(set: AttributeNameSet): Option[Length.type] =
      Some(Length).filter(set.contains)
  }
  object ContentTypeOnly {
    def unapply(set: AttributeNameSet): Option[ContentType.type] =
      if (set == contentTypeOnly) Some(ContentType) else None
  }
}
