package sttp.client

import java.net.{HttpURLConnection, URL, URLConnection}

import sttp.client.HttpURLConnectionBackend.{EncodingHandler, defaultOpenConnection}

import scala.util.Try

object TryHttpURLConnectionBackend {
  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeConnection: HttpURLConnection => Unit = _ => (),
      createURL: String => URL = new URL(_),
      openConnection: (URL, Option[java.net.Proxy]) => URLConnection = defaultOpenConnection,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  ): SttpBackend[Try, Nothing, NothingT] =
    new TryBackend[Nothing, NothingT](
      HttpURLConnectionBackend(options, customizeConnection, createURL, openConnection, customEncodingHandler)
    )
}
