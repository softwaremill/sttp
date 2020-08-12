package sttp.client.listener

import sttp.monad.MonadError
import sttp.client.{Identity, Request, Response}

/**
  * A listener to be used by the [[ListenerBackend]] to get notified on request lifecycle events.
  *
  * @tparam L Type of a value ("tag") that is associated with a request, and passed the response (or exception)
  *           is available. Use `Unit` if no special value should be associated with a request.
  */
trait RequestListener[F[_], L] {
  def beforeRequest(request: Request[_, _]): F[L]
  def requestException(request: Request[_, _], tag: L, e: Exception): F[Unit]
  def requestSuccessful(request: Request[_, _], response: Response[_], tag: L): F[Unit]
}

object RequestListener {
  def lift[F[_], L](delegate: RequestListener[Identity, L], monadError: MonadError[F]): RequestListener[F, L] =
    new RequestListener[F, L] {
      override def beforeRequest(request: Request[_, _]): F[L] = monadError.eval(delegate.beforeRequest(request))
      override def requestException(request: Request[_, _], tag: L, e: Exception): F[Unit] =
        monadError.eval(delegate.requestException(request, tag, e))
      override def requestSuccessful(request: Request[_, _], response: Response[_], tag: L): F[Unit] =
        monadError.eval(delegate.requestSuccessful(request, response, tag))
    }
}
