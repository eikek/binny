package binny.jdbc

import cats.effect.IO
import cats.effect.unsafe.implicits._

object Docker {

  def exists: IO[Boolean] =
    IO(Runtime.getRuntime.exec(Array("docker", "--version")).waitFor() == 0).attempt
      .map(_.fold(_ => false, identity))

  def existsUnsafe: Boolean =
    exists.unsafeRunSync()
}
