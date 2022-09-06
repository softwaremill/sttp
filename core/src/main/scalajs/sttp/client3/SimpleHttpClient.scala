package sttp.client3

import scala.concurrent.{ExecutionContext, Future}

/** A simple, synchronous http client. Usage example:
  *
  * {{{
  * import sttp.client3.{SttpClient, UriContext, basicRequest}
  *
  * val client = SttpClient()
  * val request = basicRequest.get(uri"https://httpbin.org/get")
  * val response = client.send(request)
  * response.map(r => println(r.body))
  * }}}
  *
  * Wraps a [[SttpBackend]], which can be substituted or modified using [[wrapBackend]], adding e.g. logging.
  *
  * Creating a client allocates resources, hence when no longer needed, the client should be closed using [[close]].
  */
case class SimpleHttpClient(backend: SttpBackend[Future, Any]) {

  def send[T](request: Request[T, Any]): Future[Response[T]] = backend.send(request)

  def withBackend(newBackend: SttpBackend[Future, Any]): SimpleHttpClient = copy(backend = newBackend)
  def wrapBackend(f: SttpBackend[Future, Any] => SttpBackend[Future, Any]): SimpleHttpClient =
    copy(backend = f(backend))

  def close(): Future[Unit] = backend.close()
}

object SimpleHttpClient {
  def apply(): SimpleHttpClient = SimpleHttpClient(FetchBackend())

  /** Runs the given function `f` with a new, default instance of [[SimpleHttpClient]] and closes the client after the
    * function completes, cleaning up any resources.
    */
  def apply[T](f: SimpleHttpClient => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val client = SimpleHttpClient()
    f(client).transformWith(r => client.close().transform(_ => r))
  }
}
