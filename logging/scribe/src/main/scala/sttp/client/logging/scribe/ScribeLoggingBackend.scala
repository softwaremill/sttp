package sttp.client.logging.scribe

import sttp.client._
import sttp.client.listener.{ListenerBackend, RequestListener}
import sttp.client.logging.LogMessages
import sttp.client.ws.WebSocketResponse

object ScribeLoggingBackend {
  def apply[F[_], S, WS_HANDLER[_]](delegate: SttpBackend[F, S, WS_HANDLER]): SttpBackend[F, S, WS_HANDLER] =
    ListenerBackend.lift(delegate, new ScribeLoggingListener())
}

class ScribeLoggingListener() extends RequestListener[Identity, Unit] {
  override def beforeRequest(request: Request[_, _]): Identity[Unit] = {
    scribe.debug(LogMessages.beforeRequestSend(request))
  }

  override def requestException(request: Request[_, _], tag: Unit, e: Exception): Identity[Unit] = {
    scribe.error(LogMessages.requestException(request), e)
  }

  override def requestSuccessful(request: Request[_, _], response: Response[_], tag: Unit): Identity[Unit] = {
    scribe.debug(LogMessages.response(request, response))
  }

  override def beforeWebsocket(request: Request[_, _]): Identity[Unit] = {
    scribe.debug(LogMessages.beforeWebsocketOpen(request))
  }

  override def websocketException(request: Request[_, _], tag: Unit, e: Exception): Identity[Unit] = {
    scribe.error(LogMessages.websocketException(request), e)
  }

  override def websocketSuccessful(
                                    request: Request[_, _],
                                    response: WebSocketResponse[_],
                                    tag: Unit
                                  ): Identity[Unit] = {
    scribe.debug(LogMessages.websocketResponse(request, response))
  }
}
