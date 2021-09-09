package binny.tika

import java.nio.charset.Charset

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

  private def charsetFromBytes(bv: Array[Byte], hint: Hint): Option[Charset] =
    //val detector = new CharsetDetector()
    ???

  private def fromBytes(
      tika: Detector,
      bv: Array[Byte],
      hint: Hint
  ): SimpleContentType = {
    val mt = convert(
      tika.detect(new java.io.ByteArrayInputStream(bv), makeMetadata(hint))
    )
    if (mt.isText)
      charsetFromBytes(bv, hint) match {
        case Some(cs) =>
          Option(MediaType.parse(mt.contentType)) match {
            case Some(mediaType) =>
              val nm = new MediaType(mediaType, cs)
              SimpleContentType(nm.toString)
            case None =>
              mt
          }
        case None =>
          mt
      }
    else mt
  }

  private def convert(mt: MediaType): SimpleContentType =
    Option(mt) match {
      case Some(_) =>
        SimpleContentType(mt.toString)
      case None =>
        SimpleContentType.octetStream
    }

}
