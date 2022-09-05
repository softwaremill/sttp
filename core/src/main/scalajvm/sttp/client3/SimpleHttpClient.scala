package sttp.client3

/** A simple, synchronous http client. Usage example:
  *
  * {{{
  * import sttp.client3.{SttpClient, UriContext, basicRequest}
  *
  * val client = SttpClient()
  * val request = basicRequest.get(uri"https://httpbin.org/get")
  * val response = client.send(request)
  * println(response.body)
  * }}}
  *
  * Wraps a [[SttpBackend]], which can be substituted or modified using [[wrapBackend]], adding e.g. logging.
  *
  * Creating a client allocates resources, hence when no longer needed, the client should be closed using [[close]].
  */
case class SimpleHttpClient(backend: SttpBackend[Identity, Any]) {

  def send[T](request: Request[T, Any]): Response[T] = backend.send(request)

  def withBackend(newBackend: SttpBackend[Identity, Any]): SimpleHttpClient = copy(backend = newBackend)
  def wrapBackend(f: SttpBackend[Identity, Any] => SttpBackend[Identity, Any]): SimpleHttpClient =
    copy(backend = f(backend))

  def close(): Unit = backend.close()
}

object SimpleHttpClient {
  def apply(): SimpleHttpClient = SimpleHttpClient(HttpClientSyncBackend())

  /** Runs the given function `f` with a new, default instance of [[SimpleHttpClient]] and closes the client after the
    * function compeltes, cleaning up any resources.
    */
  def apply[T](f: SimpleHttpClient => T): Unit = {
    val client = SimpleHttpClient()
    try f(client)
    finally client.close()
  }
}
