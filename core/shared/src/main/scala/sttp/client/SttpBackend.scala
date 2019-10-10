package sttp.client

import sttp.client.monad.MonadError

import scala.language.higherKinds

/**
  * @tparam R The type constructor in which responses are wrapped. E.g. `Identity`
  *           for synchronous backends, `Future` for asynchronous backends.
  * @tparam S The type of streams that are supported by the backend. `Nothing`,
  *           if streaming requests/responses is not supported by this backend.
  * @tparam WS_HANDLER The type of websocket handlers, that are supported by this backend.
  *                    The handler is parametrised by the value that is being returned
  *                    when the websocket is established. `NothingT`, if websockets are
  *                    not supported.
  */
trait SttpBackend[R[_], -S, -WS_HANDLER[_]] {
  def send[T](request: Request[T, S]): R[Response[T]]
  def openWebsocket[T, WS_RESULT](
      request: Request[T, S],
      handler: WS_HANDLER[WS_RESULT]
  ): R[WebSocketResponse[WS_RESULT]]

  def close(): R[Unit]

  /**
    * The monad in which the responses are wrapped. Allows writing wrapper
    * backends, which map/flatMap over the return value of [[send]].
    */
  def responseMonad: MonadError[R]
}
