package sttp.client

import java.net.HttpURLConnection

import scala.util.Try

object TryHttpURLConnectionBackend {
  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeConnection: HttpURLConnection => Unit = _ => ()
  ): SttpBackend[Try, Nothing] =
    new TryBackend[Nothing](HttpURLConnectionBackend(options, customizeConnection))
}
