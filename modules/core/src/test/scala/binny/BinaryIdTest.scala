package binny

import cats.effect._
import cats.effect.unsafe.implicits._
import fs2.Stream
import munit._
import scodec.bits.ByteVector

class BinaryIdTest extends FunSuite {

  test("create random ids") {
    val id = BinaryId.random[IO].unsafeRunSync()
    val bv = ByteVector.fromValidBase58(id.id)
    assertEquals(bv.toBase58, id.id)
  }

  test("random ids not empty") {
    Stream
      .eval(BinaryId.random[IO])
      .repeat
      .take(10)
      .map(id => assert(id.id.nonEmpty))
      .compile
      .drain
      .unsafeRunSync()
  }

  test("can not construct empty ids") {
    intercept[RuntimeException] {
      BinaryId("")
    }
  }
}
