package sttp.client3

import java.net.{HttpURLConnection, URL, URLConnection}

import sttp.client3.HttpURLConnectionBackend.{EncodingHandler, defaultOpenConnection}

import scala.util.Try

object TryHttpURLConnectionBackend {
  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeConnection: HttpURLConnection => Unit = _ => (),
      createURL: String => URL = new URL(_),
      openConnection: (URL, Option[java.net.Proxy]) => URLConnection = defaultOpenConnection,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  ): SttpBackend[Try, Any] =
    new TryBackend[Any](
      HttpURLConnectionBackend(options, customizeConnection, createURL, openConnection, customEncodingHandler)
    )
}
