package sttp.client.logging.slf4j

import org.slf4j.{Logger, LoggerFactory}
import sttp.client._
import sttp.client.listener.{ListenerBackend, RequestListener}
import sttp.client.logging.LogMessages
import sttp.client.ws.WebSocketResponse

object Slf4jLoggingBackend {
  private val logger = LoggerFactory.getLogger("sttp.client.logging.slf4j.Slf4jLoggingBackend")

  def apply[F[_], S, WS_HANDLER[_]](delegate: SttpBackend[F, S, WS_HANDLER]): SttpBackend[F, S, WS_HANDLER] =
    ListenerBackend.lift(delegate, new Slf4jLoggingListener(logger))
}

class Slf4jLoggingListener(logger: Logger) extends RequestListener[Identity, Unit] {
  override def beforeRequest(request: Request[_, _]): Identity[Unit] = {
    logger.info(LogMessages.beforeRequestSend(request))
  }

  override def requestException(request: Request[_, _], tag: Unit, e: Exception): Identity[Unit] = {
    logger.info(LogMessages.requestException(request), e)
  }

  override def requestSuccessful(request: Request[_, _], response: Response[_], tag: Unit): Identity[Unit] = {
    if (response.isSuccess) {
      logger.info(LogMessages.response(request, response))
    } else {
      logger.info(LogMessages.response(request, response))
    }
  }

  override def beforeWebsocket(request: Request[_, _]): Identity[Unit] = {
    logger.info(LogMessages.beforeWebsocketOpen(request))
  }

  override def websocketException(request: Request[_, _], tag: Unit, e: Exception): Identity[Unit] = {
    logger.info(LogMessages.websocketException(request), e)
  }

  override def websocketSuccessful(
      request: Request[_, _],
      response: WebSocketResponse[_],
      tag: Unit
  ): Identity[Unit] = {
    logger.info(LogMessages.websocketResponse(request, response))
  }
}
