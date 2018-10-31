package com.softwaremill.sttp

protected[sttp] object JsonInput {
  def sanitize[T: IsOption](s: String): String =
    if (implicitly[IsOption[T]].isOption && s.trim.isEmpty) "null" else s
}
