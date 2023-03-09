package sttp.client3

import sttp.client3.SttpClientException.{ConnectException, ReadException, TimeoutException}
import sttp.client3.ws.{GotAWebSocketException, NotAWebSocketException}

import scala.annotation.tailrec

trait SttpClientExceptionExtensions {
  @tailrec
  final def defaultExceptionToSttpClientException(request: GenericRequest[_, _], e: Exception): Option[Exception] =
    e match {
      case e: java.net.MalformedURLException        => Some(new ConnectException(request, e))
      case e: java.net.URISyntaxException           => Some(new ConnectException(request, e))
      case e: java.util.concurrent.TimeoutException => Some(new TimeoutException(request, e))
      case e: java.io.IOException                   => Some(new ReadException(request, e))
      case e: NotAWebSocketException                => Some(new ReadException(request, e))
      case e: GotAWebSocketException                => Some(new ReadException(request, e))
      case e: ResponseException[_, _]               => Some(new ReadException(request, e))
      case e if e.getCause != null && e.getCause.isInstanceOf[Exception] =>
        defaultExceptionToSttpClientException(request, e.getCause.asInstanceOf[Exception])
      case _ => None
    }
}
