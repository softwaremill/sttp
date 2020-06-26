package sttp.client

import sttp.client.monad.MonadError
import sttp.client.ws.WebSocketResponse

import scala.language.higherKinds

/**
  * @note Backends should try to classify exceptions into one of the categories specified by [[SttpClientException]].
  *       Other exceptions should be thrown unchanged.
  * @tparam F The type constructor in which responses are wrapped. E.g. [[Identity]]
  *           for synchronous backends, [[scala.concurrent.Future]] for asynchronous backends.
  * @tparam P TODO (capabilities provided by the backend)
  * @tparam WS_HANDLER The type of websocket handlers that are supported by this backend.
  *                    The handler is parametrised by the value that is being returned
  *                    when the websocket is established. [[NothingT]], if websockets are
  *                    not supported.
  */
trait SttpBackend[F[_], +P, -WS_HANDLER[_]] {
  def send[T, R >: P](request: Request[T, R]): F[Response[T]]

  /**
    * Opens a websocket, using the given backend-specific handler.
    *
    * If the connection doesn't result in a websocket being opened, a failed effect is
    * returned, or an exception is thrown (depending on `F`).
    */
  def openWebsocket[T, WS_RESULT, R >: P](
      request: Request[T, R],
      handler: WS_HANDLER[WS_RESULT]
  ): F[WebSocketResponse[WS_RESULT]]

  def close(): F[Unit]

  /**
    * The effect wrapper for responses. Allows writing wrapper backends, which map/flatMap over
    * the return value of [[send]] and [[openWebsocket]].
    */
  def responseMonad: MonadError[F]
}
