package sttp.client

import java.net.URLEncoder

import sttp.client.idn.IdnApi

private[sttp] object UriCompatibility {
  def encodeDNSHost(host: String): String = Rfc3986.encode(Rfc3986.Host)(IdnApi.toAscii(host))

  def encodeQuery(s: String, enc: String): String = URLEncoder.encode(s, enc)
}
