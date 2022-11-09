package sttp.client3

// TODO: Move to sttp-model
sealed trait HttpVersion
object HttpVersion {
  case object Default extends HttpVersion
  case object HTTP_1 extends HttpVersion
  case object HTTP_1_1 extends HttpVersion
  case object HTTP_2 extends HttpVersion
}
