package sttp.client4

import sttp.monad.MonadError

/** Known exceptions that might occur when using a backend. Currently this covers:
  *   - [[SttpClientException.ConnectException]]: when a connection (tcp socket) can't be established to the target host
  *   - [[SttpClientException.ReadException]]: when a connection has been established, but there's any kind of problem
  *     receiving or handling the response (e.g. a broken socket or a deserialization error)
  *
  * The read exceptions are further classified into:
  *   - [[SttpClientException.TimeoutException]]
  *   - [[SttpClientException.TooManyRedirectsException]]
  *   - [[SttpClientException.ResponseHandlingException]], wrapping a [[ResponseException]]
  *
  * In general, it's safe to assume that the request hasn't been sent in case of connect exceptions. With read
  * exceptions, the target host might or might have not received and processed the request.
  *
  * The [[Backend.send]] methods might also throw other exceptions, due to programming errors, bugs in the underlying
  * implementations, bugs in sttp or an uncovered exception.
  *
  * @param request
  *   The request, which was being sent when the exception was thrown
  * @param cause
  *   The original exception.
  */
abstract class SttpClientException(val request: GenericRequest[_, _], val cause: Exception)
    extends Exception(s"Exception when sending request: ${request.method} ${request.uri}", cause)

object SttpClientException extends SttpClientExceptionExtensions {
  class ConnectException(request: GenericRequest[_, _], cause: Exception) extends SttpClientException(request, cause)

  class ReadException(request: GenericRequest[_, _], cause: Exception) extends SttpClientException(request, cause)

  class TimeoutException(request: GenericRequest[_, _], cause: Exception) extends ReadException(request, cause)

  class TooManyRedirectsException(request: GenericRequest[_, _], val redirects: Int)
      extends ReadException(request, null)

  /** Wraps a [[ResponseException]] which occurred during response handling. Enriches the response exception with the
    * context of the request, for which it happened.
    */
  class ResponseHandlingException[+HE](request: GenericRequest[_, _], val responseException: ResponseException[HE])
      extends ReadException(request, responseException)

  def adjustExceptions[F[_], T](
      monadError: MonadError[F]
  )(t: => F[T])(usingFn: Exception => Option[Exception]): F[T] =
    monadError.handleError(t) { case e: Exception =>
      monadError.error(usingFn(e).getOrElse(e))
    }
}
