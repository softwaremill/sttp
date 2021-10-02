package sttp.client3.listener

import sttp.capabilities.Effect
import sttp.client3._
import sttp.monad.syntax._

/** A backend wrapper which notifies the given [[RequestListener]] when a request starts and completes.
  */
class ListenerBackend[F[_], P, L](
    delegate: SttpBackend[F, P],
    listener: RequestListener[F, L]
) extends DelegateSttpBackend[F, P](delegate) {

  override def send[T, R >: P with Effect[F]](request: Request[T, R]): F[Response[T]] = {
    listener.beforeRequest(request).flatMap { t =>
      responseMonad
        .handleError(delegate.send(request)) { case e: Exception =>
          listener.requestException(request, t, e).flatMap(_ => responseMonad.error(e))
        }
        .flatMap { response => listener.requestSuccessful(request, response, t).map(_ => response) }
    }
  }
}

object ListenerBackend {
  def lift[F[_], S, L](
      delegate: SttpBackend[F, S],
      listener: RequestListener[Identity, L]
  ): ListenerBackend[F, S, L] = {
    new ListenerBackend(delegate, RequestListener.lift(listener, delegate.responseMonad))
  }
}
