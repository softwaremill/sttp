package sttp.client

import sttp.client.monad.{MonadError, TryMonad}
import sttp.client.ws.WebSocketResponse

import scala.language.higherKinds
import scala.util.Try

/** A synchronous backend that safely wraps [[SttpBackend]] exceptions in `Try`'s
  *
  * @param delegate A synchronous `SttpBackend` which to which this backend forwards all requests
  * @tparam P TODO
  * @tparam WS_HANDLER The type of websocket handlers, that are supported by this backend.
  *                    The handler is parametrised by the value that is being returned
  *                    when the websocket is established. `NothingT`, if websockets are
  *                    not supported.
  */
class TryBackend[P, WS_HANDLER[_]](delegate: SttpBackend[Identity, P, WS_HANDLER])
    extends SttpBackend[Try, P, WS_HANDLER] {
  override def send[T, R >: P](request: Request[T, R]): Try[Response[T]] =
    Try(delegate.send(request))

  override def openWebsocket[T, WS_RESULT, R >: P](
      request: Request[T, R],
      handler: WS_HANDLER[WS_RESULT]
  ): Try[WebSocketResponse[WS_RESULT]] =
    Try(delegate.openWebsocket(request, handler))

  override def close(): Try[Unit] = Try(delegate.close())

  override def responseMonad: MonadError[Try] = TryMonad
}
