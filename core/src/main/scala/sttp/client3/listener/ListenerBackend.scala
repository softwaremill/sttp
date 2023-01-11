package sttp.client3.listener

import sttp.client3._
import sttp.monad.syntax._
import sttp.capabilities.Effect

/** A backend wrapper which notifies the given [[RequestListener]] when a request starts and completes.
  */
abstract class ListenerBackend[F[_], P, L](
    delegate: AbstractBackend[F, P],
    listener: RequestListener[F, L]
) extends DelegateSttpBackend(delegate) {
  override def internalSend[T](request: AbstractRequest[T, P with Effect[F]]): F[Response[T]] = {
    listener.beforeRequest(request).flatMap { t =>
      responseMonad
        .handleError(delegate.internalSend(request)) { case e: Exception =>
          listener.requestException(request, t, e).flatMap(_ => responseMonad.error(e))
        }
        .flatMap { response => listener.requestSuccessful(request, response, t).map(_ => response) }
    }
  }
}

object ListenerBackend {
  def lift[F[_], L](delegate: Backend[F], listener: RequestListener[Identity, L]): Backend[F] =
    apply(delegate, RequestListener.lift(listener, delegate.responseMonad))

  def apply[L](delegate: SyncBackend, listener: RequestListener[Identity, L]): SyncBackend =
    new ListenerBackend(delegate, listener) with SyncBackend {}

  def apply[F[_], L](delegate: Backend[F], listener: RequestListener[F, L]): Backend[F] =
    new ListenerBackend(delegate, listener) with Backend[F]

  def apply[F[_], L](delegate: WebSocketBackend[F], listener: RequestListener[F, L]): WebSocketBackend[F] =
    new ListenerBackend(delegate, listener) with WebSocketBackend[F]

  def apply[F[_], S, L](delegate: StreamBackend[F, S], listener: RequestListener[F, L]): StreamBackend[F, S] =
    new ListenerBackend(delegate, listener) with StreamBackend[F, S]

  def apply[F[_], S, L](
      delegate: WebSocketStreamBackend[F, S],
      listener: RequestListener[F, L]
  ): WebSocketStreamBackend[F, S] =
    new ListenerBackend(delegate, listener) with WebSocketStreamBackend[F, S]
}
