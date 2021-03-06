package binny

import java.net.URLConnection
import java.nio.file.{Files, Paths}

import scala.util.Try

import fs2.{Pipe, Stream}
import scodec.bits.ByteVector

/** A way to detect the content type of some bytes. */
trait ContentTypeDetect { self =>

  def detect(data: ByteVector, hint: Hint): SimpleContentType

  def detectStream[F[_]](hint: Hint): Pipe[F, Byte, SimpleContentType] =
    data => data.chunkN(50).head.map(ch => self.detect(ch.toByteVector, hint))

  /** Try another detector if this can't do it */
  def or(next: ContentTypeDetect): ContentTypeDetect =
    ContentTypeDetect((bv, hint) => self.detect(bv, hint).or(next.detect(bv, hint)))
}

object ContentTypeDetect {

  /** Always returns `application/octet-stream` */
  val none: ContentTypeDetect = new ContentTypeDetect {
    def detect(data: ByteVector, hint: Hint): SimpleContentType =
      SimpleContentType.octetStream

    override def detectStream[F[_]](hint: Hint): Pipe[F, Byte, SimpleContentType] =
      _ => Stream.emit(SimpleContentType.octetStream)

    override def or(next: ContentTypeDetect): ContentTypeDetect =
      next
  }

  /** An implementation using Java7 `java.nio.file.spi.FileTypeDetector` */
  val probeFileType: ContentTypeDetect =
    ContentTypeDetect { (_, hint) =>
      hint.filename
        .flatMap(name => Try(Paths.get(name)).toOption)
        .flatMap(name =>
          Option(Files.probeContentType(name)).orElse(
            Option(
              // this is not always tried by probeContentType it seems
              URLConnection.getFileNameMap.getContentTypeFor(name.getFileName.toString)
            )
          )
        )
        .map(SimpleContentType.apply)
        .getOrElse(SimpleContentType.octetStream)
    }

  def constant(ct: SimpleContentType): ContentTypeDetect =
    ContentTypeDetect((_, _) => ct)

  def apply(f: (ByteVector, Hint) => SimpleContentType): ContentTypeDetect =
    (data: ByteVector, hint: Hint) => f(data, hint)

}
