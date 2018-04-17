package com.softwaremill.sttp

import java.net.URI

private[sttp] object UriCompatibility {

  def encodeDNSHost(str: String): String = new URI(s"http://$str").getHost

  def encodeQuery(str: String, enc: String): String = {
    val chars = str.getBytes(enc).map(_.toChar)
    val encChars = chars.flatMap(ch => {
      if (shouldEncode(ch)) {
        encodeChar(ch).getBytes(enc)
      } else {
        Array(ch.toByte)
      }
    })

    new String(encChars, enc)
  }

  private def shouldEncode(char: Char): Boolean =
    (char < 31 || char > 127) &&
      Set(':', '/', '@', '!', '$', '\'', '(', ')', '*', '+', ',', ';', ' ', '%', '<', '>', '[', ']', '#', '{', '}', '^',
        '`', '|', '?', '&', '\\', '=', '"').contains(char)

  private def encodeChar(char: Char): String = "%" + "%04x".format(char.toInt).substring(2).toUpperCase

}
