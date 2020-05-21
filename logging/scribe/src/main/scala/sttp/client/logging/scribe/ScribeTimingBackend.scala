package sttp.client.logging.scribe

import sttp.client._
import sttp.client.listener.{ListenerBackend, RequestListener}
import sttp.client.logging.LogMessages
import sttp.client.ws.WebSocketResponse

object ScribeTimingBackend {
  def apply[F[_], S, WS_HANDLER[_]](delegate: SttpBackend[F, S, WS_HANDLER]): SttpBackend[F, S, WS_HANDLER] =
    ListenerBackend.lift(delegate, new ScribeTimingListener())
}

class ScribeTimingListener() extends RequestListener[Identity, Long] {
  private def now(): Long = System.currentTimeMillis()

  private def elapsed(from: Long): Long = now() - from

  override def beforeRequest(request: Request[_, _]): Identity[Long] = now()

  override def requestException(request: Request[_, _], tag: Long, e: Exception): Identity[Unit] = {
    scribe.info(LogMessages.requestTiming(request, "exception", elapsed(tag)), e)
  }

  override def requestSuccessful(request: Request[_, _], response: Response[_], tag: Long): Identity[Unit] = {
    scribe.info(LogMessages.requestTiming(request, response.code.toString(), elapsed(tag)))
  }

  override def beforeWebsocket(request: Request[_, _]): Identity[Long] = now()

  override def websocketException(request: Request[_, _], tag: Long, e: Exception): Identity[Unit] = {
    scribe.info(LogMessages.requestTiming(request, "exception", elapsed(tag)), e)
  }

  override def websocketSuccessful(
                                    request: Request[_, _],
                                    response: WebSocketResponse[_],
                                    tag: Long
                                  ): Identity[Unit] = {
    scribe.info(LogMessages.requestTiming(request, "websocket", elapsed(tag)))
  }
}
