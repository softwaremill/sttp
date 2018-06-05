package com.softwaremill.sttp

private[sttp] object UriCompatibility {

  def encodeDNSHost(host: String): String = {
    Rfc3986.encode(Rfc3986.Host)(host)
  }

  def encodeQuery(s: String, enc: String): String = {
    Rfc3986.encode(Rfc3986.Query, enc = enc)(s)
  }
}
