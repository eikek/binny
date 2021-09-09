package binny

import binny.ContentTypeDetect.Hint
import fs2.{Chunk, Stream}
import munit._
import scodec.bits.ByteVector

class BinaryAttributesTest extends FunSuite {

  test("compute attributes from empty stream") {
    val empty = Stream.empty.covaryOutput[Byte]
    val attr =
      empty.through(BinaryAttributes.compute(ContentTypeDetect.none, Hint.none))

    val expect = BinaryAttributes(
      ByteVector.fromValidHex(
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
      ),
      SimpleContentType.octetStream,
      0
    )
    assertEquals(attr.toList.head, expect)
  }

  test("compute attributes from non-empty stream") {
    val hello = Stream.chunk(Chunk.array("hello".getBytes))
    val attr =
      hello.through(BinaryAttributes.compute(ContentTypeDetect.none, Hint.none))

    val expect = BinaryAttributes(
      ByteVector.fromValidHex(
        "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
      ),
      SimpleContentType.octetStream,
      5
    )
    assertEquals(attr.toList.head, expect)
  }

  test("fromString and asString") {
    val expect = BinaryAttributes(
      ByteVector.fromValidHex(
        "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
      ),
      SimpleContentType.octetStream,
      5
    )
    val str    = BinaryAttributes.asString(expect)
    val loaded = BinaryAttributes.unsafeFromString(str)
    assertEquals(loaded, expect)
  }
}
