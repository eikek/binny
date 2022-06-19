package binny

sealed trait InsertChunkResult

object InsertChunkResult {
  sealed trait Success extends InsertChunkResult
  sealed trait Failure extends InsertChunkResult

  case object Complete extends Success

  case object Incomplete extends Success

  final case class InvalidChunkSize(msg: String) extends Failure

  final case class InvalidChunkIndex(msg: String) extends Failure

  def complete: InsertChunkResult = Complete

  def incomplete: InsertChunkResult = Incomplete

  def invalidChunkSize(msg: String): InsertChunkResult = InvalidChunkSize(msg)

  def invalidChunkIndex(msg: String): InsertChunkResult = InvalidChunkIndex(msg)

  private[binny] def validateChunk(
      chunkDef: ChunkDef,
      chunkSize: Int,
      actualSize: Int
  ): Option[InsertChunkResult] = {
    val ch = chunkDef.fold(identity, _.toTotal(chunkSize))
    val isLastChunk = ch.index == ch.total - 1
    if (ch.index < 0 || ch.index >= ch.total)
      Some(
        InsertChunkResult
          .invalidChunkIndex(
            s"Index ${ch.index} must not be negative or >= total chunks ${ch.total}"
          )
      )
    else if (isLastChunk && actualSize > chunkSize)
      Some(
        InsertChunkResult
          .invalidChunkSize(
            s"Chunk exceeds chunk size: $actualSize > $chunkSize"
          )
      )
    else if (!isLastChunk && actualSize != chunkSize)
      Some(
        InsertChunkResult
          .invalidChunkSize(
            s"Chunk ($actualSize) does not match chunk size ($chunkSize)"
          )
      )
    else None
  }
}
