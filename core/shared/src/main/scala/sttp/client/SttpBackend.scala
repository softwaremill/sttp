package sttp.client

import sttp.client.monad.MonadError
import sttp.client.ws.WebSocketResponse

import scala.language.higherKinds

/**
  * @tparam F The type constructor in which responses are wrapped. E.g. `Identity`
  *           for synchronous backends, `Future` for asynchronous backends.
  * @tparam S The type of streams that are supported by the backend. `Nothing`,
  *           if streaming requests/responses is not supported by this backend.
  * @tparam WS_HANDLER The type of websocket handlers, that are supported by this backend.
  *                    The handler is parametrised by the value that is being returned
  *                    when the websocket is established. `NothingT`, if websockets are
  *                    not supported.
  */
trait SttpBackend[F[_], -S, -WS_HANDLER[_]] {
  def send[T](request: Request[T, S]): F[Response[T]]

  /**
    * Opens a websocket, using the given backend-specific handler.
    *
    * If the connection doesn't result in a websocket being opened, a failed effect is
    * returned, or an exception is thrown (depending on `F`).
    */
  def openWebsocket[T, WS_RESULT](
      request: Request[T, S],
      handler: WS_HANDLER[WS_RESULT]
  ): F[WebSocketResponse[WS_RESULT]]

  def close(): F[Unit]

  /**
    * The effect wrapper for responses. Allows writing wrapper backends, which map/flatMap over
    * the return value of [[send]] and [[openWebsocket]].
    */
  def responseMonad: MonadError[F]
}
