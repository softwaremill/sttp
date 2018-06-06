package com.softwaremill.sttp

import com.softwaremill.sttp.curl.CurlApi
import com.softwaremill.sttp.curl.CurlApi._

private[sttp] object UriCompatibility {

  def encodeDNSHost(host: String): String = {
    val curl = CurlApi.init
    val enc = curl.encode(host)
    curl.cleanup()
    enc
  }

  def encodeQuery(s: String, enc: String): String = {
    val curl = CurlApi.init
    val enc = curl.encode(s)
    curl.cleanup()
    enc
  }
}
