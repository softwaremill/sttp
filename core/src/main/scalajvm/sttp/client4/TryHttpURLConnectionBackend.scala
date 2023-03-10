package sttp.client4

import java.net.{HttpURLConnection, URL, URLConnection}

import sttp.client4.HttpURLConnectionBackend.{EncodingHandler, defaultOpenConnection}

import scala.util.Try

object TryHttpURLConnectionBackend {
  def apply(
             options: BackendOptions = BackendOptions.Default,
             customizeConnection: HttpURLConnection => Unit = _ => (),
             createURL: String => URL = new URL(_),
             openConnection: (URL, Option[java.net.Proxy]) => URLConnection = defaultOpenConnection,
             customEncodingHandler: EncodingHandler = PartialFunction.empty
  ): Backend[Try] = TryBackend(
    HttpURLConnectionBackend(options, customizeConnection, createURL, openConnection, customEncodingHandler)
  )
}
