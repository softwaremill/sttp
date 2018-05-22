package com.softwaremill.sttp

import java.net.URLEncoder

private[sttp] object UriCompatibility {

  def toASCII(s: String): String = java.net.IDN.toASCII(s)

  def formatByte(byte: Byte): String = "%02X".format(byte)

  def encodeQuery(s: String, enc: String): String = URLEncoder.encode(s, enc)
}
