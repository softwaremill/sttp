package sttp.client

import java.net.URLEncoder

private[client] object UriCompatibility {

  def encodeDNSHost(host: String): String = {
    Rfc3986.encode(Rfc3986.Host)(java.net.IDN.toASCII(host))
  }

  def encodeQuery(s: String, enc: String): String = URLEncoder.encode(s, enc)
}
