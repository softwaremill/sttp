package sttp.client

import sttp.client.monad.MonadError

import scala.annotation.tailrec

/**
  * Known exceptions that might occur when using a backend. Currently this covers:
  * - connect exceptions: when a connection (tcp socket) can't be established to the target host
  * - read exceptions: when a connection has been established, but there's any kind of problem receiving the response
  *   (e.g. a broken socket)
  *
  * In general, it's safe to assume that the request hasn't been sent in case of connect exceptions. With read
  * exceptions, the target host might or might have not received and processed the request.
  *
  * The [[SttpBackend.send]] and [[SttpBackend.openWebsocket]] methods might also throw other exceptions, due to
  * programming errors, bugs in the underlying implementations, bugs in sttp or an uncovered exception.
  *
  * @param cause The original exception.
  */
abstract class SttpClientException(cause: Exception) extends Exception(cause)

object SttpClientException {
  class ConnectException(cause: Exception) extends SttpClientException(cause)

  class ReadException(cause: Exception) extends SttpClientException(cause)

  @tailrec
  def defaultExceptionToSttpClientException(e: Exception): Option[Exception] = e match {
    case e: java.net.ConnectException             => Some(new ConnectException(e))
    case e: java.net.UnknownHostException         => Some(new ConnectException(e))
    case e: java.net.MalformedURLException        => Some(new ConnectException(e))
    case e: java.net.NoRouteToHostException       => Some(new ConnectException(e))
    case e: java.net.PortUnreachableException     => Some(new ConnectException(e))
    case e: java.net.ProtocolException            => Some(new ConnectException(e))
    case e: java.net.URISyntaxException           => Some(new ConnectException(e))
    case e: java.net.SocketTimeoutException       => Some(new ReadException(e))
    case e: java.net.UnknownServiceException      => Some(new ReadException(e))
    case e: java.net.SocketException              => Some(new ReadException(e))
    case e: java.util.concurrent.TimeoutException => Some(new ReadException(e))
    case e: java.io.IOException                   => Some(new ReadException(e))
    case e if e.getCause != null && e.getCause.isInstanceOf[Exception] =>
      defaultExceptionToSttpClientException(e.getCause.asInstanceOf[Exception])
    case _ => None
  }

  def adjustSynchronousExceptions[T](t: => T)(usingFn: Exception => Option[Exception]): T = {
    try t
    catch {
      case e: Exception => throw usingFn(e).getOrElse(e)
    }
  }

  def adjustExceptions[F[_], T](
      monadError: MonadError[F]
  )(t: => F[T])(usingFn: Exception => Option[Exception]): F[T] = {
    monadError.handleError(t) {
      case e: Exception => monadError.error(usingFn(e).getOrElse(e))
    }
  }
}
