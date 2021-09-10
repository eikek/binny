package binny

sealed trait ByteRange {

  def asString: String

}

object ByteRange {

  case object All extends ByteRange {
    val asString = "all"
  }
  final case class Chunk(offset: Long, length: Long) extends ByteRange {
    val asString = s"$offset,$length"
  }

  def apply(offset: Long, length: Long): ByteRange =
    Chunk(offset, length)
}
