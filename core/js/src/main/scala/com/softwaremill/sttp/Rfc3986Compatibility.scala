package com.softwaremill.sttp

object Rfc3986Compatibility {

  def formatByte(byte: Byte): String = {
    // negative bytes have leading F on scalajs
    // https://github.com/scala-js/scala-js/issues/2206
    "%02X".format(byte).takeRight(2)
  }
}
