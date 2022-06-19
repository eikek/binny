package binny.fs

sealed trait OverwriteMode
object OverwriteMode {
  case object Fail extends OverwriteMode
  case object Skip extends OverwriteMode
  case object Replace extends OverwriteMode
}
