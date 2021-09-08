package binny

import scodec.bits.ByteVector

/** Basic attributes of binary data. */
final case class BinaryAttributes(
  sha256: ByteVector,
  contentType: String,
  length: Long
)

object BinaryAttributes {

}
