package com.softwaremill.sttp

import java.net.HttpURLConnection

import scala.util.Try

/** A Backend that safely wraps SttpBackend exceptions in Try's
  *
  * @param delegate An SttpBackend which to which this backend forwards all requests
  * @tparam S The type of streams that are supported by the backend. `Nothing`,
  *           if streaming requests/responses is not supported by this backend.
  */
class TryBackend[-S](delegate: SttpBackend[Id, S]) extends SttpBackend[Try, S] {
  override def send[T](request: Request[T, S]): Try[Response[T]] =
    Try(delegate.send(request))

  override def close(): Unit = delegate.close()

  override def responseMonad: MonadError[Try] = TryMonad
}

object TryHttpURLConnectionBackend {
  def apply(options: SttpBackendOptions = SttpBackendOptions.Default,
            customizeConnection: HttpURLConnection => Unit = _ => ()): SttpBackend[Try, Nothing] =
    new TryBackend[Nothing](HttpURLConnectionBackend(options, customizeConnection))
}
