package com.softwaremill.sttp

import org.scalajs.dom.experimental.URL

import scala.scalajs.js.URIUtils

private[sttp] object UriCompatibility {

  def toASCII(s: String): String = {
    // use the URL class to IDN format the host
    new URL(s"http://$s").host
  }

  def formatByte(byte: Byte): String = {
    // negative bytes have leading F on scalajs
    // https://github.com/scala-js/scala-js/issues/2206
    "%02X".format(byte).takeRight(2)
  }

  def encodeQuery(s: String, enc: String): String = URIUtils.encodeURIComponent(s)
}
