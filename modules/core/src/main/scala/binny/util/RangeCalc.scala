package binny.util

import binny.ByteRange
import fs2.{Chunk, Pure, Stream}

object RangeCalc {

  /** Offsets counters for a sequence of chunks that make up the complete file to retrieve
    * only a part from it.
    *
    * @param firstChunk
    *   the number of the first chunk (starting from 0)
    * @param takeChunks
    *   how many chunks to take starting from `firstChunk`
    * @param dropStart
    *   how many bytes to drop from the first chunk
    * @param takeEnd
    *   how many bytes to take from the last chunk. If first and last chunk are the same,
    *   then the `dropStart` _must_ be applied first
    */
  final case class Offsets(
      firstChunk: Int,
      takeChunks: Int,
      dropStart: Int,
      takeEnd: Int
  ) {
    val lastChunk = firstChunk + takeChunks - 1

    def isNone: Boolean =
      this == Offsets.none

    def isChunked: Boolean =
      !isNone
  }

  object Offsets {
    val none: Offsets =
      Offsets(0, Int.MaxValue, 0, 0)
  }

  /** Calculates the offsets to use when fetching only a `range` from a file. The given
    * `chunkSize` is size for each chunk except the last one (which may have fewer bytes).
    *
    * @param range
    *   the given range that should be retrieved
    * @param chunkSize
    *   the overall chunk size used; the last chunk may be smaller
    */
  def calcOffset(range: ByteRange, chunkSize: Int): Offsets =
    range match {
      case ByteRange.All =>
        Offsets.none

      case ByteRange.Chunk(offset, length) =>
        val firstChunk = (offset / chunkSize).toInt
        val lastChunk = ((offset + length) / chunkSize).toInt
        val startOffset = (offset % chunkSize).toInt
        val endOffset =
          ((offset + length) % chunkSize).toInt - (if (firstChunk == lastChunk)
                                                     startOffset
                                                   else 0)
        val nChunk =
          lastChunk - firstChunk + (if (startOffset == 0 && endOffset == 0) 0 else 1)
        Offsets(firstChunk, nChunk, startOffset, endOffset)
    }

  /** Give a chunk, chops off some bytes according to the give offsets. This can only
    * affect the first and last chunk. The `index` defines which chunk it is.
    */
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

  private type Modify = Chunk[Byte] => Chunk[Byte]
  private object Modify {
    def when(cond: Boolean)(f: Modify): Modify =
      if (cond) f else identity
  }

  /** Given a chunkSize, generates an possibly infinite stream of chunk definitions. */
  def calcChunks(range: ByteRange, chunkSize: Int): Stream[Pure, ByteRange.Chunk] = {
    def go(start: Int): Stream[Pure, ByteRange.Chunk] =
      Stream.emit(ByteRange.Chunk(start, chunkSize)) ++ go(start + chunkSize).repeat

    range match {
      case ByteRange.All =>
        go(0)

      case ByteRange.Chunk(off, len) =>
        val rest = len % chunkSize
        val count = len / chunkSize
        val stream = go(off.toInt).take(count)
        if (rest == 0) stream
        else stream ++ Stream.emit(ByteRange.Chunk(off + (count * chunkSize), rest))
    }

  }
}
