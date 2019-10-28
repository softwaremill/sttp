package sttp.model.internal

import scala.util.matching.Regex

// https://tools.ietf.org/html/rfc2616#page-21
object Rfc2616 {
  val CTL = "\\x00-\\x1F\\x7F"
  val Separators = "()<>@,;:\\\\\"/\\[\\]?={} \\x09"
  private val TokenRegexPart = s"[^$Separators$CTL]*"
  val Token: Regex = TokenRegexPart.r
  val Parameter: Regex = s"TokenRegexPart=TokenRegexPart".r

  def validateToken(componentName: String, v: String): Option[String] = {
    if (Rfc2616.Token.unapplySeq(v).isEmpty) {
      Some(s"""$componentName can not contain separators: ()<>@,;:\"/[]?={}, or whitespace.""")
    } else None
  }
}
