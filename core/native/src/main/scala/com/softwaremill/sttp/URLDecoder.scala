package com.softwaremill.sttp

import java.io.ByteArrayOutputStream

/**
  * copied from https://github.com/scala-native/scala-native/blob/master/javalib/src/main/scala/java/net/URIEncoderDecoder.scala
  * fix after https://github.com/scala-native/scala-native/issues/1241 is resolved
  */
object URLDecoder {
  def decode(s: String, encoding: String): String = {
    val result: StringBuilder = new StringBuilder()
    val out: ByteArrayOutputStream = new ByteArrayOutputStream()
    var i: Int = 0
    while (i < s.length) {
      val c: Char = s.charAt(i)
      if (c == '%') {
        out.reset()
        do {
          if (i + 2 >= s.length) {
            throw new IllegalArgumentException("Incomplete % sequence at: " + i)
          }
          val d1: Int = java.lang.Character.digit(s.charAt(i + 1), 16)
          val d2: Int = java.lang.Character.digit(s.charAt(i + 2), 16)
          if (d1 == -1 || d2 == -1) {
            throw new IllegalArgumentException("Invalid % sequence (" + s.substring(i, i + 3) + ") at: " + i)
          }
          out.write(((d1 << 4) + d2).toInt)
          i += 3
        } while (i < s.length && s.charAt(i) == '%')
        result.append(out.toString(encoding.toUpperCase))
      }
      result.append(c)
      i += 1
    }
    result.toString
  }
}
