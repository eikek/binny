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

  val lengthOnly: NonEmptySet[AttributeName] =
    select(Length)

  val contentTypeOnly: NonEmptySet[AttributeName] =
    select(ContentType)

  val sha256Only: NonEmptySet[AttributeName] =
    select(Sha256)

  val all: NonEmptySet[AttributeName] =
    select(Length, Sha256, ContentType)

  val excludeSha256: NonEmptySet[AttributeName] =
    select(Length, ContentType)

  def select(ba: AttributeName, bas: AttributeName*): NonEmptySet[AttributeName] =
    NonEmptySet.of(ba, bas: _*)

  object ContainsSha256 {
    def unapply(set: NonEmptySet[AttributeName]): Option[Sha256.type] =
      Some(Sha256).filter(set.contains)
  }
  object ContainsLength {
    def unapply(set: NonEmptySet[AttributeName]): Option[Length.type] =
      Some(Length).filter(set.contains)
  }
  object ContentTypeOnly {
    def unapply(set: NonEmptySet[AttributeName]): Option[ContentType.type] =
      if (set == contentTypeOnly) Some(ContentType) else None
  }
}
