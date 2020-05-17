package sttp.client

import sttp.client.monad.{EitherMonad, MonadError}
import sttp.client.ws.WebSocketResponse

import scala.language.higherKinds
import scala.util.control.NonFatal

/** A synchronous backend that safely wraps [[SttpBackend]] exceptions in `Either[Throwable, ?]`'s
  *
  * @param delegate A synchronous `SttpBackend` which to which this backend forwards all requests
  * @tparam S The type of streams that are supported by the backend. `Nothing`,
  *           if streaming requests/responses is not supported by this backend.
  * @tparam WS_HANDLER The type of websocket handlers, that are supported by this backend.
  *                    The handler is parametrised by the value that is being returned
  *                    when the websocket is established. `NothingT`, if websockets are
  *                    not supported.
  */
class EitherBackend[S, WS_HANDLER[_]](delegate: SttpBackend[Identity, S, WS_HANDLER])
    extends SttpBackend[Either[Throwable, *], S, WS_HANDLER] {
  override def send[T](request: Request[T, S]): Either[Throwable, Response[T]] = doTry(delegate.send(request))

  override def openWebsocket[T, WS_RESULT](
      request: Request[T, S],
      handler: WS_HANDLER[WS_RESULT]
  ): Either[Throwable, WebSocketResponse[WS_RESULT]] =
    doTry(delegate.openWebsocket(request, handler))

  override def close(): Either[Throwable, Unit] = doTry(delegate.close())

  private def doTry[T](t: => T): Either[Throwable, T] = {
    try Right(t)
    catch {
      case NonFatal(e) => Left(e)
    }
  }

  override def responseMonad: MonadError[Either[Throwable, *]] = EitherMonad
}
