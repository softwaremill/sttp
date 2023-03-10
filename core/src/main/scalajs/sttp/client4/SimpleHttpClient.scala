package sttp.client4

import scala.concurrent.{ExecutionContext, Future}

/** A simple asynchronous http client. Usage example:
  *
  * {{{
  * import sttp.client4.{SimpleHttpClient, UriContext, basicRequest}
  *
  * val client = SimpleHttpClient()
  * val request = basicRequest.get(uri"https://httpbin.org/get")
  * val response = client.send(request)
  * response.map(r => println(r.body))
  * }}}
  *
  * Wraps an [[Backend[Future]]], which can be substituted or modified using [[wrapBackend]], adding e.g. logging.
  *
  * Creating a client allocates resources, hence when no longer needed, the client should be closed using [[close]].
  */
case class SimpleHttpClient(backend: Backend[Future]) {

  def send[T](request: Request[T]): Future[Response[T]] = backend.send(request)

  def withBackend(newBackend: Backend[Future]): SimpleHttpClient = copy(backend = newBackend)
  def wrapBackend(f: Backend[Future] => Backend[Future]): SimpleHttpClient = copy(backend = f(backend))

  def close(): Future[Unit] = backend.close()
}

object SimpleHttpClient {
  def apply(): SimpleHttpClient = SimpleHttpClient(FetchBackend())

  /** Runs the given function `f` with a new, default instance of [[SimpleHttpClient]] and closes the client after the
    * function completes, cleaning up any resources.
    */
  def apply[T](f: SimpleHttpClient => Future[T])(implicit ec: ExecutionContext): Future[T] = {
    val client = SimpleHttpClient()
    f(client)
      .map(r => client.close().map(_ => r))
      .recover { case t: Throwable =>
        client.close().map(_ => Future.failed[T](t)).recover { case _ => Future.failed[T](t) }.flatMap(identity)
      }
      .flatMap(identity)
  }
}
