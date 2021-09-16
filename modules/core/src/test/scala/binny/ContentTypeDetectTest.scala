package binny

import munit._
import scodec.bits.ByteVector

class ContentTypeDetectTest extends FunSuite {

  test("detect via filename") {
    assertEquals(
      ContentTypeDetect.probeFileType
        .detect(ByteVector.empty, Hint.filename("index.html")),
      SimpleContentType("text/html")
    )
  }

  test("chain") {
    val dt1 = ContentTypeDetect.none.or(ContentTypeDetect.probeFileType)
    assertEquals(
      dt1.detect(ByteVector.empty, Hint.filename("text.html")),
      SimpleContentType("text/html")
    )

    val dt2 = ContentTypeDetect.probeFileType.or(ContentTypeDetect.none)
    assertEquals(
      dt2.detect(ByteVector.empty, Hint.filename("text.html")),
      SimpleContentType("text/html")
    )
  }

  test("chain argument lazily") {
    val ct = ContentTypeDetect((_, _) => sys.error("error"))
    val dt = ContentTypeDetect.probeFileType.or(ct)
    assertEquals(
      dt.detect(ByteVector.empty, Hint.filename("text.html")),
      SimpleContentType("text/html")
    )
  }
}
