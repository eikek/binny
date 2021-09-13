package binny.tika

import binny.ContentTypeDetect.Hint
import binny.{ContentTypeDetect, SimpleContentType}
import org.apache.tika.config.TikaConfig
import org.apache.tika.detect.Detector
import org.apache.tika.metadata.{HttpHeaders, Metadata, TikaCoreProperties}
import org.apache.tika.mime.MediaType
import scodec.bits.ByteVector

final class TikaContentTypeDetect(tika: Detector) extends ContentTypeDetect {
  def detect(data: ByteVector, hint: Hint): SimpleContentType =
    TikaContentTypeDetect.fromBytes(tika, data.toArray, hint)
}

object TikaContentTypeDetect {

  def default: TikaContentTypeDetect =
    fromTikaConfig(new TikaConfig())

  def fromTikaConfig(tc: TikaConfig): TikaContentTypeDetect =
    new TikaContentTypeDetect(tc.getDetector)

  private def makeMetadata(hint: Hint): Metadata = {
    val md = new Metadata
    hint.filename.foreach(md.set(TikaCoreProperties.RESOURCE_NAME_KEY, _))
    hint.advertisedType.foreach(md.set(HttpHeaders.CONTENT_TYPE, _))
    md
  }

  private def fromBytes(
      tika: Detector,
      bv: Array[Byte],
      hint: Hint
  ): SimpleContentType =
    convert(tika.detect(new java.io.ByteArrayInputStream(bv), makeMetadata(hint)))

  private def convert(mt: MediaType): SimpleContentType =
    Option(mt) match {
      case Some(_) =>
        SimpleContentType(mt.toString)
      case None =>
        SimpleContentType.octetStream
    }
}
