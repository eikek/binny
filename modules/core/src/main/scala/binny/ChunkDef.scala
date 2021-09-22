package binny

import binny.ChunkDef.{Length, Total}

sealed trait ChunkDef {
  def fold[A](fa: Total => A, fb: Length => A): A
}

object ChunkDef {
  def fromTotal(index: Int, totalChunks: Int): ChunkDef =
    Total(index, totalChunks)

  def fromLength(index: Int, totalLength: Long): ChunkDef =
    Length(index, totalLength)

  final case class Total(index: Int, total: Int) extends ChunkDef {
    def fold[A](fa: Total => A, fb: Length => A): A =
      fa(this)
  }

  final case class Length(index: Int, length: Long) extends ChunkDef {
    def fold[A](fa: Total => A, fb: Length => A): A =
      fb(this)

    def toTotal(chunkSize: Int): Total = {
      val nChunks = length / chunkSize + (if (length % chunkSize == 0) 0 else 1)
      Total(index, nChunks.toInt)
    }
  }
}
