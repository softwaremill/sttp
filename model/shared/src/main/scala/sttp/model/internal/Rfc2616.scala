package sttp.model.internal

import scala.util.matching.Regex

// https://tools.ietf.org/html/rfc2616#page-21
object Rfc2616 {
  val CTL = "\\x00-\\x1F\\x7F"
  val Separators = "()<>@,;:\\\\\"/\\[\\]?={} \\x09"
  val Token: Regex = s"[^$Separators$CTL]*".r
}
