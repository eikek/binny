package binny

import munit._
import scodec.bits.ByteVector

class BinaryAttributesTest extends FunSuite {

  test("compute attributes from empty stream") {
    val attr =
      Binary.empty.through(
        ComputeAttr.computeAll(ContentTypeDetect.none, Hint.none)
      )

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
    val attr =
      ExampleData.helloWorld.through(
        ComputeAttr.computeAll(ContentTypeDetect.none, Hint.none)
      )

    val expect = BinaryAttributes(
      ByteVector.fromValidHex(
        "7f83b1657ff1fc53b92dc18148a1d65dfc2d4b1fa3d677284addd200126d9069"
      ),
      SimpleContentType.octetStream,
      12
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
    val str = BinaryAttributes.asString(expect)
    val loaded = BinaryAttributes.unsafeFromString(str)
    assertEquals(loaded, expect)
  }
}
