package binny

sealed trait ByteRange {

  def asString: String

  def includes(other: ByteRange): Boolean

}

object ByteRange {

  case object All extends ByteRange {
    val asString = "all"
    def includes(other: ByteRange) = true
  }
  final case class Chunk(offset: Long, length: Long) extends ByteRange {
    val asString = s"$offset,$length"
    def includes(other: ByteRange) = other match {
      case All => offset == 0 && length == Long.MaxValue
      case Chunk(off, len) =>
        off >= offset && len <= length
    }
  }

  def apply(offset: Long, length: Long): ByteRange =
    Chunk(offset, length)
}
