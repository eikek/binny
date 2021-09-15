package binny

import munit.FunSuite

class SimpleContentTypeTest extends FunSuite {

  val textHtml = SimpleContentType("text/html")

  test("empty to octet-stream") {
    assertEquals(SimpleContentType(""), SimpleContentType.octetStream)
    assertEquals(SimpleContentType("  "), SimpleContentType.octetStream)
  }

  test("chain") {
    assertEquals(textHtml, textHtml.or(SimpleContentType.octetStream))
    assertEquals(textHtml, SimpleContentType.octetStream.or(textHtml))
  }

  test("chain lazily") {
    assertEquals(textHtml, textHtml.or(sys.error("error")))
  }
}
