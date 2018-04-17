package com.softwaremill.sttp
object URLDecoder {
  def decode(s: String, enc: String): String = java.net.URLDecoder.decode(s, enc)
}
