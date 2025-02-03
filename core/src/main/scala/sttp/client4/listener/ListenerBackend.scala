package sttp.client4.listener

import sttp.client4._
import sttp.monad.syntax._
import sttp.capabilities.Effect
import sttp.client4.wrappers.DelegateBackend
import sttp.shared.Identity

/** A backend wrapper which notifies the given [[RequestListener]] when a request starts and completes. */
abstract class ListenerBackend[F[_], P, L](
    delegate: GenericBackend[F, P],
    listener: RequestListener[F, L]
) extends DelegateBackend(delegate) {
  override def send[T](request: GenericRequest[T, P with Effect[F]]): F[Response[T]] =
    listener.beforeRequest(request).flatMap { case (requestToSend, tag) =>
      monad
        .handleError(delegate.send(requestToSend)) { case e: Exception =>
          monad.flatMap {
            ResponseException.find(e) match {
              case Some(re) => listener.requestSuccessful(requestToSend, re.response, tag, Some(re))
              case None     => listener.requestException(requestToSend, tag, e)
            }
          } { _ => monad.error(e) }
        }
        .flatMap(response => listener.requestSuccessful(requestToSend, response, tag, None).map(_ => response))
    }
}

object ListenerBackend {
  def lift[F[_], L](delegate: Backend[F], listener: RequestListener[Identity, L]): Backend[F] =
    apply(delegate, RequestListener.lift(listener, delegate.monad))

  def apply[L](delegate: SyncBackend, listener: RequestListener[Identity, L]): SyncBackend =
    new ListenerBackend(delegate, listener) with SyncBackend {}

  def apply[F[_], L](delegate: Backend[F], listener: RequestListener[F, L]): Backend[F] =
    new ListenerBackend(delegate, listener) with Backend[F]

  def apply[F[_], L](delegate: WebSocketBackend[F], listener: RequestListener[F, L]): WebSocketBackend[F] =
    new ListenerBackend(delegate, listener) with WebSocketBackend[F]

  def apply[L](delegate: WebSocketSyncBackend, listener: RequestListener[Identity, L]): WebSocketSyncBackend =
    new ListenerBackend(delegate, listener) with WebSocketSyncBackend {}

  def apply[F[_], S, L](delegate: StreamBackend[F, S], listener: RequestListener[F, L]): StreamBackend[F, S] =
    new ListenerBackend(delegate, listener) with StreamBackend[F, S]

  def apply[F[_], S, L](
      delegate: WebSocketStreamBackend[F, S],
      listener: RequestListener[F, L]
  ): WebSocketStreamBackend[F, S] =
    new ListenerBackend(delegate, listener) with WebSocketStreamBackend[F, S]
}
