package binny

import cats.effect._
import scodec.bits.ByteVector

import java.security.SecureRandom

final class BinaryId private(val id: String) extends AnyVal {

  override def toString: String = s"BinaryId($id)"

}

object BinaryId {

  def apply(id: String): BinaryId =
    if (id.isEmpty) sys.error("Empty ids not allowed")
    else new BinaryId(id)


  def random[F[_]: Sync]: F[BinaryId] = Sync[F].delay {
    val bytes = randomBytes(32)
    BinaryId(ByteVector.view(bytes).toBase58)
  }

  private def randomBytes(len: Int): Array[Byte] = {
    val rnd = new SecureRandom()
    val array = new Array[Byte](len)
    rnd.nextBytes(array)
    array
  }
}
