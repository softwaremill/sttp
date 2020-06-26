package sttp.client

import sttp.client.monad.{EitherMonad, MonadError}
import sttp.client.ws.WebSocketResponse

import scala.language.higherKinds
import scala.util.control.NonFatal

/** A synchronous backend that safely wraps [[SttpBackend]] exceptions in `Either[Throwable, ?]`'s
  *
  * @param delegate A synchronous `SttpBackend` which to which this backend forwards all requests
  * @tparam P TODO
  * @tparam WS_HANDLER The type of websocket handlers, that are supported by this backend.
  *                    The handler is parametrised by the value that is being returned
  *                    when the websocket is established. `NothingT`, if websockets are
  *                    not supported.
  */
class EitherBackend[P, WS_HANDLER[_]](delegate: SttpBackend[Identity, P, WS_HANDLER])
    extends SttpBackend[Either[Throwable, *], P, WS_HANDLER] {
  override def send[T, R >: P](request: Request[T, R]): Either[Throwable, Response[T]] = doTry(delegate.send(request))

  override def openWebsocket[T, WS_RESULT, R >: P](
      request: Request[T, R],
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
