package sttp.client.logging.slf4j

import org.slf4j.{Logger, LoggerFactory}
import sttp.client.listener.{ListenerBackend, RequestListener}
import sttp.client.logging.LogMessages
import sttp.client.ws.WebSocketResponse
import sttp.client.{Identity, Request, Response, SttpBackend}

object Slf4jCurlBackend {
  private val logger = LoggerFactory.getLogger("sttp.client.logging.slf4j.Slf4jCurlBackend")

  def apply[F[_], S, WS_HANDLER[_]](delegate: SttpBackend[F, S, WS_HANDLER]): SttpBackend[F, S, WS_HANDLER] =
    ListenerBackend.lift(delegate, new Slf4jCurlListener(logger))
}

class Slf4jCurlListener(logger: Logger) extends RequestListener[Identity, Unit] {
  override def beforeRequest(request: Request[_, _]): Identity[Unit] = ()

  override def requestException(request: Request[_, _], tag: Unit, e: Exception): Identity[Unit] = {
    logger.info(LogMessages.requestCurl(request, "exception"), e)
  }

  override def requestSuccessful(request: Request[_, _], response: Response[_], tag: Unit): Identity[Unit] = {
    logger.info(LogMessages.requestCurl(request, response.code.toString()))
  }

  override def beforeWebsocket(request: Request[_, _]): Identity[Unit] = ()

  override def websocketException(request: Request[_, _], tag: Unit, e: Exception): Identity[Unit] = {
    logger.info(LogMessages.requestCurl(request, "exception"), e)
  }

  override def websocketSuccessful(
      request: Request[_, _],
      response: WebSocketResponse[_],
      tag: Unit
  ): Identity[Unit] = {
    logger.info(LogMessages.requestCurl(request, "websocket"))
  }
}
