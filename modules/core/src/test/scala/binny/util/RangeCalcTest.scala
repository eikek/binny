package binny.util

import binny.ByteRange
import fs2.Chunk
import munit.FunSuite

class RangeCalcTest extends FunSuite {

  test("inside same chunk") {
    val offsets = RangeCalc.calcOffset(ByteRange(24, 120), 200)
    assertEquals(offsets.firstChunk, 0)
    assertEquals(offsets.takeChunks, 1)
    assertEquals(offsets.lastChunk, 0)
    assertEquals(offsets.dropStart, 24)
    assertEquals(offsets.takeEnd, 120)

    val chunk = Chunk.from(Vector.range(0, 200).map(_.toByte))
    val ch = RangeCalc.chop(chunk, offsets, 0)
    assertEquals(ch.size, 120)
    assertEquals(ch, Chunk.from(Vector.range(24, 144).map(_.toByte)))
  }

  test("multiple chunks, first and last partial") {
    val offsets = RangeCalc.calcOffset(ByteRange(35, 75), 15)
    assertEquals(offsets.firstChunk, 2)
    assertEquals(offsets.takeChunks, 6)
    assertEquals(offsets.lastChunk, 7)
    assertEquals(offsets.dropStart, 5)
    assertEquals(offsets.takeEnd, 5)

    val chunk = Chunk.from(Vector.range(0, 15).map(_.toByte))

    val fc = RangeCalc.chop(chunk, offsets, offsets.firstChunk)
    assertEquals(fc.size, 10)

    val lc = RangeCalc.chop(chunk, offsets, offsets.lastChunk)
    assertEquals(lc.size, 5)

    val mc = RangeCalc.chop(chunk, offsets, 3)
    assertEquals(mc.size, 15)
  }

  test("exactly one chunk") {
    val offsets = RangeCalc.calcOffset(ByteRange(40, 20), 20)
    assertEquals(offsets.firstChunk, 2)
    assertEquals(offsets.takeChunks, 1)
    assertEquals(offsets.lastChunk, 2)
    assertEquals(offsets.dropStart, 0)
    assertEquals(offsets.takeEnd, 0)

    val chunk = Chunk.from(Vector.range(0, 20).map(_.toByte))

    val fc = RangeCalc.chop(chunk, offsets, offsets.firstChunk)
    assertEquals(fc, chunk)

    val lc = RangeCalc.chop(chunk, offsets, offsets.lastChunk)
    assertEquals(lc, chunk)
  }

  test("start at chunk start") {
    val offsets = RangeCalc.calcOffset(ByteRange(40, 10), 20)
    assertEquals(offsets.firstChunk, 2)
    assertEquals(offsets.takeChunks, 1)
    assertEquals(offsets.lastChunk, 2)
    assertEquals(offsets.dropStart, 0)
    assertEquals(offsets.takeEnd, 10)

    val chunk = Chunk.from(Vector.range(0, 20).map(_.toByte))
    val fc = RangeCalc.chop(chunk, offsets, offsets.firstChunk)
    assertEquals(fc.size, 10)
  }

  test("calcChunks with rest") {
    assertEquals(
      RangeCalc.calcChunks(ByteRange(10, 70), 20).toVector,
      Vector(chunk(10, 20), chunk(30, 20), chunk(50, 20), chunk(70, 10))
    )
  }

  test("calcChunks no rest") {
    assertEquals(
      RangeCalc.calcChunks(ByteRange(10, 60), 20).toVector,
      Vector(chunk(10, 20), chunk(30, 20), chunk(50, 20))
    )
  }

  test("calcChunks small range") {
    assertEquals(
      RangeCalc.calcChunks(ByteRange(5, 10), 2000).toVector,
      Vector(chunk(5, 10))
    )
  }

  def chunk(offset: Int, len: Int): ByteRange.Chunk =
    ByteRange.Chunk(offset, len)
}
