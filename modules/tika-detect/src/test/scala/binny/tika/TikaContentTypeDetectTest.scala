package binny.tika

import binny.ContentTypeDetect.Hint
import binny.SimpleContentType
import munit.CatsEffectSuite
import scodec.bits.ByteVector

class TikaContentTypeDetectTest extends CatsEffectSuite {

  val td = TikaContentTypeDetect.default

  def ct(s: String) = SimpleContentType(s)
  def detect(bv: ByteVector, hint: Hint) = td.detect(bv, hint)

  test("detect text/plain") {
    val mt = detect(ByteVector.view("hello world".getBytes), Hint.none)
    assertEquals(mt, ct("text/plain"))
  }

  test("detect image/jpeg") {
    val mt = detect(ByteVector.fromValidBase64("/9j/4AAQSkZJRgABAgAAZABkAAA="), Hint.none)
    assertEquals(mt, ct("image/jpeg"))
  }

  test("detect image/png") {
    val mt = detect(ByteVector.fromValidBase64("iVBORw0KGgoAAAANSUhEUgAAA2I="), Hint.none)
    assertEquals(mt, ct("image/png"))
  }

  test("detect application/json") {
    val mt =
      detect(ByteVector.view("""{"name":"me"}""".getBytes), Hint.filename("me.json"))
    assertEquals(mt, ct("application/json"))
  }

  test("detect application/json") {
    val mt = detect(
      ByteVector.view("""{"name":"me"}""".getBytes),
      Hint.advertised("application/json")
    )
    assertEquals(mt, ct("application/json"))
  }

  test("detect image/jpeg wrong advertised") {
    val mt = detect(
      ByteVector.fromValidBase64("/9j/4AAQSkZJRgABAgAAZABkAAA="),
      Hint.advertised("image/png")
    )
    assertEquals(mt, ct("image/jpeg"))
  }

  test("just filename") {
    assertEquals(
      detect(ByteVector.empty, Hint.filename("doc.pdf")),
      ct("application/pdf")
    )
  }
}
