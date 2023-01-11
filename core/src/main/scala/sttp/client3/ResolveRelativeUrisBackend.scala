package sttp.client3

import sttp.capabilities.Effect
import sttp.model.Uri
import sttp.monad.syntax._

abstract class ResolveRelativeUrisBackend[F[_], P](delegate: AbstractBackend[F, P], resolve: Uri => F[Uri])
    extends DelegateSttpBackend(delegate) {

  override def internalSend[T](request: AbstractRequest[T, P with Effect[F]]): F[Response[T]] = {
    val request2 = if (request.uri.isRelative) {
      resolve(request.uri).map { uri2 =>
        request.method(method = request.method, uri = uri2)
      }
    } else request.unit

    request2.flatMap(delegate.internalSend)
  }
}

object ResolveRelativeUrisBackend {
  def apply(delegate: SyncBackend, baseUri: Uri): SyncBackend =
    new ResolveRelativeUrisBackend[Identity, Any](delegate, baseUri.resolve) with SyncBackend {}

  def apply[F[_]](delegate: Backend[F], baseUri: Uri): Backend[F] =
    new ResolveRelativeUrisBackend(delegate, uri => delegate.responseMonad.unit(baseUri.resolve(uri)))
      with Backend[F] {}

  def apply[F[_]](delegate: WebSocketBackend[F], baseUri: Uri): WebSocketBackend[F] =
    new ResolveRelativeUrisBackend(delegate, uri => delegate.responseMonad.unit(baseUri.resolve(uri)))
      with WebSocketBackend[F] {}

  def apply[F[_], S](delegate: StreamBackend[F, S], baseUri: Uri): StreamBackend[F, S] =
    new ResolveRelativeUrisBackend(delegate, uri => delegate.responseMonad.unit(baseUri.resolve(uri)))
      with StreamBackend[F, S] {}

  def apply[F[_], S](delegate: WebSocketStreamBackend[F, S], baseUri: Uri): WebSocketStreamBackend[F, S] =
    new ResolveRelativeUrisBackend(delegate, uri => delegate.responseMonad.unit(baseUri.resolve(uri)))
      with WebSocketStreamBackend[F, S] {}
}
