package binny.util

import fs2.Pipe

private[binny] object StreamUtil {

  def zipWithIndexFrom[F[_], A](n: Int): Pipe[F, A, (A, Long)] =
    _.zipWithIndex.map(pt => (pt._1, pt._2 + n))
}
