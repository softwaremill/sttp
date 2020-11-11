package sttp.client3

import sttp.client3.ws.{GotAWebSocketException, NotAWebSocketException}
import sttp.monad.MonadError

import scala.annotation.tailrec

/** Known exceptions that might occur when using a backend. Currently this covers:
  * - connect exceptions: when a connection (tcp socket) can't be established to the target host
  * - read exceptions: when a connection has been established, but there's any kind of problem receiving or
  *   handling the response (e.g. a broken socket or a deserialization error)
  *
  * In general, it's safe to assume that the request hasn't been sent in case of connect exceptions. With read
  * exceptions, the target host might or might have not received and processed the request.
  *
  * The [[SttpBackend.send]] methods might also throw other exceptions, due to
  * programming errors, bugs in the underlying implementations, bugs in sttp or an uncovered exception.
  *
  * @param request The request, which was being sent when the exception was thrown
  * @param cause The original exception.
  */
abstract class SttpClientException(request: Request[_, _], cause: Exception)
    extends Exception(s"Exception when sending request: ${request.method} ${request.uri}", cause)

object SttpClientException {
  class ConnectException(request: Request[_, _], cause: Exception) extends SttpClientException(request, cause)

  class ReadException(request: Request[_, _], cause: Exception) extends SttpClientException(request, cause)

  @tailrec
  def defaultExceptionToSttpClientException(request: Request[_, _], e: Exception): Option[Exception] =
    e match {
      case e: java.net.ConnectException             => Some(new ConnectException(request, e))
      case e: java.net.UnknownHostException         => Some(new ConnectException(request, e))
      case e: java.net.MalformedURLException        => Some(new ConnectException(request, e))
      case e: java.net.NoRouteToHostException       => Some(new ConnectException(request, e))
      case e: java.net.PortUnreachableException     => Some(new ConnectException(request, e))
      case e: java.net.ProtocolException            => Some(new ConnectException(request, e))
      case e: java.net.URISyntaxException           => Some(new ConnectException(request, e))
      case e: java.net.SocketTimeoutException       => Some(new ReadException(request, e))
      case e: java.net.UnknownServiceException      => Some(new ReadException(request, e))
      case e: java.net.SocketException              => Some(new ReadException(request, e))
      case e: java.util.concurrent.TimeoutException => Some(new ReadException(request, e))
      case e: java.io.IOException                   => Some(new ReadException(request, e))
      case e: NotAWebSocketException                => Some(new ReadException(request, e))
      case e: GotAWebSocketException                => Some(new ReadException(request, e))
      case e: ResponseException[_, _]               => Some(new ReadException(request, e))
      case e if e.getCause != null && e.getCause.isInstanceOf[Exception] =>
        defaultExceptionToSttpClientException(request, e.getCause.asInstanceOf[Exception])
      case _ => None
    }

  def adjustExceptions[F[_], T](
      monadError: MonadError[F]
  )(t: => F[T])(usingFn: Exception => Option[Exception]): F[T] = {
    monadError.handleError(t) { case e: Exception =>
      monadError.error(usingFn(e).getOrElse(e))
    }
  }
}
