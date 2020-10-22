package sttp.client3

import sttp.capabilities.Effect
import sttp.client3.DigestAuthenticationBackend._
import sttp.client3.internal.DigestAuthenticator
import sttp.client3.internal.DigestAuthenticator.DigestAuthData
import sttp.monad.MonadError
import sttp.monad.syntax._
import sttp.model.Header

class DigestAuthenticationBackend[F[_], P](
    delegate: SttpBackend[F, P],
    clientNonceGenerator: () => String = DigestAuthenticator.defaultClientNonceGenerator
) extends SttpBackend[F, P] {
  private implicit val m: MonadError[F] = responseMonad

  override def send[T, R >: P with Effect[F]](request: Request[T, R]): F[Response[T]] = {
    delegate
      .send(request)
      .flatMap { firstResponse =>
        handleResponse(request, firstResponse, ProxyDigestAuthTag, DigestAuthenticator.proxy(_, clientNonceGenerator))
      }
      .flatMap { case (secondResponse, proxyAuthHeader) =>
        handleResponse(
          proxyAuthHeader.map(h => request.header(h)).getOrElse(request),
          secondResponse,
          DigestAuthTag,
          DigestAuthenticator.apply(_, clientNonceGenerator)
        ).map(_._1)
      }
  }

  private def handleResponse[T, R >: P with Effect[F]](
      request: Request[T, R],
      response: Response[T],
      digestTag: String,
      digestAuthenticator: DigestAuthData => DigestAuthenticator
  ): F[(Response[T], Option[Header])] = {
    request
      .tag(digestTag)
      .map(_.asInstanceOf[DigestAuthData])
      .flatMap { digestAuthData =>
        val header = digestAuthenticator(digestAuthData).authenticate(request, response)
        header.map(h => delegate.send(request.header(h)).map(_ -> Option(h)))
      }
      .getOrElse((response -> Option.empty[Header]).unit)
  }

  override def close(): F[Unit] = delegate.close()
  override def responseMonad: MonadError[F] = delegate.responseMonad
}

object DigestAuthenticationBackend {
  private[client3] val DigestAuthTag = "__sttp_DigestAuth"
  private[client3] val ProxyDigestAuthTag = "__sttp_ProxyDigestAuth"
}
