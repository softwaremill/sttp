package sttp.client3

// FIXME: Move to sttp-model ?
sealed trait HttpVersion
case object Empty extends HttpVersion
case object HTTP_1 extends HttpVersion
case object HTTP_1_1 extends HttpVersion
case object HTTP_2 extends HttpVersion
