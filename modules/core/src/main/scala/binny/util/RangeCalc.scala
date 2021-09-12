package binny.util

import binny.ByteRange
import fs2.Chunk

object RangeCalc {

  final case class Offsets(
      firstChunk: Int,
      takeChunks: Int,
      dropStart: Int,
      takeEnd: Int
  ) {
    val lastChunk = firstChunk + takeChunks - 1
  }

  def calcOffset(range: ByteRange, chunkSize: Int): Offsets =
    range match {
      case ByteRange.All =>
        Offsets(0, Int.MaxValue, 0, 0)

      case ByteRange.Chunk(offset, length) =>
        val firstChunk  = (offset / chunkSize).toInt
        val lastChunk   = ((offset + length) / chunkSize).toInt
        val startOffset = (offset % chunkSize).toInt
        val endOffset =
          ((offset + length) % chunkSize).toInt - (if (firstChunk == lastChunk)
                                                     startOffset
                                                   else 0)
        val nChunk =
          lastChunk - firstChunk + (if (startOffset == 0 && endOffset == 0) 0 else 1)
        Offsets(firstChunk, nChunk, startOffset, endOffset)
    }

  def chop(ch: Chunk[Byte], offsets: Offsets, index: Int): Chunk[Byte] = {
    val mods: List[Modify] = List(
      Modify.when(index == offsets.lastChunk && offsets.takeEnd > 0)(
        _.take(offsets.takeEnd)
      ),
      Modify.when(index == offsets.firstChunk && offsets.dropStart > 0)(
        _.drop(offsets.dropStart)
      )
    )
    mods.foldRight(ch)(_.apply(_))
  }

  type Modify = Chunk[Byte] => Chunk[Byte]
  object Modify {
    def when(cond: Boolean)(f: Modify): Modify =
      if (cond) f else identity
  }

}
