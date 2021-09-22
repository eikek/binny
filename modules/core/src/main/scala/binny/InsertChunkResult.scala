package binny

sealed trait InsertChunkResult

object InsertChunkResult {

  case object Complete extends InsertChunkResult

  case object Incomplete extends InsertChunkResult

  final case class InvalidChunkSize(msg: String) extends InsertChunkResult

  final case class InvalidChunkIndex(msg: String) extends InsertChunkResult

  def complete: InsertChunkResult = Complete
  def incomplete: InsertChunkResult = Incomplete
  def invalidChunkSize(msg: String): InsertChunkResult = InvalidChunkSize(msg)
  def invalidChunkIndex(msg: String): InsertChunkResult = InvalidChunkIndex(msg)
}
