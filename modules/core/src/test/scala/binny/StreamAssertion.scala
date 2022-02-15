package binny

import cats.effect.IO
import fs2.Stream
import munit.FunSuite

trait StreamAssertion { self: FunSuite =>

  def shaString(s: Stream[IO, Byte]) =
    s.through(fs2.hash.sha256)
      .through(fs2.text.hex.encode)
      .compile
      .string

  def assertBinaryEquals(s1: Binary[IO], s2: Binary[IO]): IO[Unit] =
    for {
      str1 <- shaString(s1)
      str2 <- shaString(s2)
      _ = assertEquals(str1, str2)
    } yield ()

  def assertExistAndEquals(s1: Option[Binary[IO]], s2: Option[Binary[IO]]): IO[Unit] =
    (s1, s2) match {
      case (Some(b1), Some(b2)) => assertBinaryEquals(b1, b2)
      case _                    => IO(fail("One of the streams is not defined"))
    }
}
