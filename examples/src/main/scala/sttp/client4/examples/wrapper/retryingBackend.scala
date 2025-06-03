// {cat=Backend wrapper; effects=Synchronous; backend=HttpClient}: Simple retrying backend wrapper

//> using dep com.softwaremill.sttp.client4::core:4.0.8

package sttp.client4.examples.wrapper

import sttp.capabilities.Effect
import sttp.client4.*
import sttp.client4.wrappers.DelegateBackend

class RetryingBackendWrapper[F[_], P](delegate: GenericBackend[F, P], shouldRetry: RetryWhen, maxRetries: Int)
    extends DelegateBackend(delegate):
  override def send[T](request: GenericRequest[T, P with Effect[F]]): F[Response[T]] =
    sendWithRetryCounter(request, 0)

  private def sendWithRetryCounter[T](request: GenericRequest[T, P with Effect[F]], retries: Int): F[Response[T]] =

    val r = monad.handleError(delegate.send(request)):
      case t if shouldRetry(request, Left(t)) && retries < maxRetries =>
        sendWithRetryCounter(request, retries + 1)

    monad.flatMap(r): resp =>
      if shouldRetry(request, Right(resp)) && retries < maxRetries
      then
        println(s"Retrying, attempt $retries/$maxRetries")
        sendWithRetryCounter(request, retries + 1)
      else monad.unit(resp)

object RetryingBackendWrapper:
  def apply(backend: WebSocketSyncBackend, shouldRetry: RetryWhen, maxRetries: Int): WebSocketSyncBackend =
    new RetryingBackendWrapper(backend, shouldRetry, maxRetries) with WebSocketSyncBackend {}

  def apply[F[_], L](backend: Backend[F], shouldRetry: RetryWhen, maxRetries: Int): Backend[F] =
    new RetryingBackendWrapper(backend, shouldRetry, maxRetries) with Backend[F]

  // depending on the backend & effect type used, other "apply" variants might be needed here

@main def retryingBackend(): Unit =
  val backend: WebSocketSyncBackend = RetryingBackendWrapper(DefaultSyncBackend(), RetryWhen.Default, 3)
  val response: Response[Either[String, String]] = basicRequest.get(uri"https://httpbin.org/status/500").send(backend)

  println(s"Got response code: ${response.code}")
  println(response.body)
