package sttp.client4.wrappers

import sttp.capabilities.Effect
import sttp.client4.{
  Backend,
  GenericBackend,
  GenericRequest,
  Identity,
  Response,
  StreamBackend,
  SyncBackend,
  WebSocketBackend,
  WebSocketStreamBackend
}
import sttp.model.Uri
import sttp.monad.syntax._

abstract class ResolveRelativeUrisBackend[F[_], P](delegate: GenericBackend[F, P], resolve: Uri => F[Uri])
    extends DelegateBackend(delegate) {

  override def send[T](request: GenericRequest[T, P with Effect[F]]): F[Response[T]] = {
    val request2 = if (request.uri.isRelative) {
      resolve(request.uri).map { uri2 =>
        request.method(method = request.method, uri = uri2)
      }
    } else request.unit

    request2.flatMap(delegate.send)
  }
}

object ResolveRelativeUrisBackend {
  def apply(delegate: SyncBackend, baseUri: Uri): SyncBackend =
    new ResolveRelativeUrisBackend[Identity, Any](delegate, baseUri.resolve) with SyncBackend {}
  def apply[F[_]](delegate: Backend[F], baseUri: Uri): Backend[F] =
    new ResolveRelativeUrisBackend(delegate, uri => delegate.monad.unit(baseUri.resolve(uri))) with Backend[F] {}
  def apply[F[_]](delegate: WebSocketBackend[F], baseUri: Uri): WebSocketBackend[F] =
    new ResolveRelativeUrisBackend(delegate, uri => delegate.monad.unit(baseUri.resolve(uri)))
      with WebSocketBackend[F] {}
  def apply[F[_], S](delegate: StreamBackend[F, S], baseUri: Uri): StreamBackend[F, S] =
    new ResolveRelativeUrisBackend(delegate, uri => delegate.monad.unit(baseUri.resolve(uri)))
      with StreamBackend[F, S] {}
  def apply[F[_], S](delegate: WebSocketStreamBackend[F, S], baseUri: Uri): WebSocketStreamBackend[F, S] =
    new ResolveRelativeUrisBackend(delegate, uri => delegate.monad.unit(baseUri.resolve(uri)))
      with WebSocketStreamBackend[F, S] {}
}
