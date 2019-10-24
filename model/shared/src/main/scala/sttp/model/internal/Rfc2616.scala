package sttp.model.internal

import scala.util.matching.Regex

object Rfc2616 {
  val Token: Regex = "[^()<>@,;:\\\\\"/\\[\\]?={} \t\r\n\f]*".r
}
