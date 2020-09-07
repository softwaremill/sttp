package sttp.client.logging.slf4j

import sttp.client.listener.{ListenerBackend, RequestListener}
import sttp.client.logging.LogMessages
import sttp.client.{Identity, Request, Response, SttpBackend}

object Slf4jCurlBackend {
  private val logger = new Logger("sttp.client.logging.slf4j.Slf4jCurlBackend")

  def apply[F[_], S](delegate: SttpBackend[F, S]): SttpBackend[F, S] =
    ListenerBackend.lift(delegate, new Slf4jCurlListener(logger))
}

class Slf4jCurlListener(logger: Logger) extends RequestListener[Identity, Unit] {
  override def beforeRequest(request: Request[_, _]): Identity[Unit] = ()

  override def requestException(request: Request[_, _], tag: Unit, e: Exception): Identity[Unit] = {
    logger.debug(LogMessages.requestCurl(request, "exception"), e)
  }

  override def requestSuccessful(request: Request[_, _], response: Response[_], tag: Unit): Identity[Unit] = {
    logger.debug(LogMessages.requestCurl(request, response.code.toString()))
  }
}
