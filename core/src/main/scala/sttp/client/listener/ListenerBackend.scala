package sttp.client.listener

import sttp.client._
import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
import sttp.client.ws.WebSocketResponse

/**
  * A backend wrapper which notifies the given [[RequestListener]] when a request starts and completes.
  */
class ListenerBackend[F[_], S, WS_HANDLER[_], L](
    delegate: SttpBackend[F, S, WS_HANDLER],
    listener: RequestListener[F, L]
) extends SttpBackend[F, S, WS_HANDLER] {

  override def send[T](request: Request[T, S]): F[Response[T]] = {
    listener.beforeRequest(request).flatMap { t =>
      responseMonad
        .handleError(delegate.send(request)) { case e: Exception =>
          listener.requestException(request, t, e).flatMap(_ => responseMonad.error(e))
        }
        .flatMap { response => listener.requestSuccessful(request, response, t).map(_ => response) }
    }
  }
  override def openWebsocket[T, WS_RESULT](
      request: Request[T, S],
      handler: WS_HANDLER[WS_RESULT]
  ): F[WebSocketResponse[WS_RESULT]] = {
    listener.beforeWebsocket(request).flatMap { t =>
      responseMonad
        .handleError(delegate.openWebsocket(request, handler)) { case e: Exception =>
          listener.websocketException(request, t, e).flatMap(_ => responseMonad.error(e))
        }
        .flatMap { response => listener.websocketSuccessful(request, response, t).map(_ => response) }
    }
  }

  override def close(): F[Unit] = delegate.close()
  override implicit def responseMonad: MonadError[F] = delegate.responseMonad
}

object ListenerBackend {
  def lift[F[_], S, WS_HANDLER[_], L](
      delegate: SttpBackend[F, S, WS_HANDLER],
      listener: RequestListener[Identity, L]
  ): ListenerBackend[F, S, WS_HANDLER, L] = {
    new ListenerBackend(delegate, RequestListener.lift(listener, delegate.responseMonad))
  }
}
