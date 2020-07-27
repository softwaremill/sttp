package sttp.client

import java.net.HttpURLConnection

import scala.util.Try

object TryHttpURLConnectionBackend {
  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeConnection: HttpURLConnection => Unit = _ => ()
  ): SttpBackend[Try, Any, NothingT] =
    new TryBackend[Any, NothingT](HttpURLConnectionBackend(options, customizeConnection))
}
