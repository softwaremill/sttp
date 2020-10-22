package sttp.client3

protected[sttp] object JsonInput {
  def sanitize[T: IsOption]: String => String = { s =>
    if (implicitly[IsOption[T]].isOption && s.trim.isEmpty) "null" else s
  }
}
