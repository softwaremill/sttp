package sttp.client3

import sttp.capabilities.Effect
import sttp.model.Uri
import sttp.monad.syntax._

class ResolveRelativeUrisBackend[F[_], +P](delegate: SttpBackend[F, P], resolve: Uri => F[Uri])
    extends DelegateSttpBackend[F, P](delegate) {

  override def send[T, R >: P with Effect[F]](request: AbstractRequest[T, R]): F[Response[T]] = {
    val request2 = if (request.uri.isRelative) {
      resolve(request.uri).map { uri2 =>
        request.method(method = request.method, uri = uri2)
      }
    } else request.unit

    request2.flatMap(delegate.send)
  }
}

object ResolveRelativeUrisBackend {
  def apply[F[_], P](delegate: SttpBackend[F, P], baseUri: Uri): ResolveRelativeUrisBackend[F, P] =
    new ResolveRelativeUrisBackend(delegate, (uri: Uri) => delegate.responseMonad.unit(baseUri.resolve(uri)))
}
