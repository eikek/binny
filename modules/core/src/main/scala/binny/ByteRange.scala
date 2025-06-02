package binny

sealed trait ByteRange {

  def asString: String

  def includes(other: ByteRange): Boolean

  def fold[A](fa: => A, fc: ByteRange.Chunk => A): A
}

object ByteRange {

  case object All extends ByteRange {
    val asString = "all"
    def includes(other: ByteRange) = true
    def fold[A](fa: => A, fc: ByteRange.Chunk => A) =
      fa
  }
  final case class Chunk(offset: Long, length: Int) extends ByteRange {
    val asString = s"$offset,$length"
    def includes(other: ByteRange) = other match {
      case All             => offset == 0 && length == Long.MaxValue
      case Chunk(off, len) =>
        off >= offset && len <= length
    }
    def fold[A](fa: => A, fc: ByteRange.Chunk => A) =
      fc(this)
  }

  def apply(offset: Long, length: Int): ByteRange =
    Chunk(offset, length)
}
