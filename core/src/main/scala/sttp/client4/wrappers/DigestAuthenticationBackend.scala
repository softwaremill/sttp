package sttp.client4.wrappers

import sttp.capabilities.Effect
import sttp.client4.internal.DigestAuthenticator
import sttp.client4.internal.DigestAuthenticator.DigestAuthData
import sttp.client4.wrappers.DigestAuthenticationBackend._
import sttp.client4.{Backend, GenericBackend, GenericRequest, Response, StreamBackend, SyncBackend, WebSocketBackend, WebSocketStreamBackend}
import sttp.model.Header
import sttp.monad.syntax._

abstract class DigestAuthenticationBackend[F[_], P] private (
    delegate: GenericBackend[F, P],
    clientNonceGenerator: () => String
) extends DelegateBackend(delegate) {

  override def send[T](request: GenericRequest[T, P with Effect[F]]): F[Response[T]] =
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

  private def handleResponse[T](
                                 request: GenericRequest[T, P with Effect[F]],
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
}

object DigestAuthenticationBackend {
  def apply(delegate: SyncBackend): SyncBackend = apply(delegate, DigestAuthenticator.defaultClientNonceGenerator _)
  def apply[F[_]](delegate: Backend[F]): Backend[F] = apply(delegate, DigestAuthenticator.defaultClientNonceGenerator _)
  def apply[F[_]](delegate: WebSocketBackend[F]): WebSocketBackend[F] =
    apply(delegate, DigestAuthenticator.defaultClientNonceGenerator _)
  def apply[F[_], S](delegate: StreamBackend[F, S]): StreamBackend[F, S] =
    apply(delegate, DigestAuthenticator.defaultClientNonceGenerator _)
  def apply[F[_], S](delegate: WebSocketStreamBackend[F, S]): WebSocketStreamBackend[F, S] =
    apply(delegate, DigestAuthenticator.defaultClientNonceGenerator _)
  def apply(delegate: SyncBackend, clientNonceGenerator: () => String): SyncBackend =
    new DigestAuthenticationBackend(delegate, clientNonceGenerator) with SyncBackend {}
  def apply[F[_]](delegate: Backend[F], clientNonceGenerator: () => String): Backend[F] =
    new DigestAuthenticationBackend(delegate, clientNonceGenerator) with Backend[F] {}
  def apply[F[_]](delegate: WebSocketBackend[F], clientNonceGenerator: () => String): WebSocketBackend[F] =
    new DigestAuthenticationBackend(delegate, clientNonceGenerator) with WebSocketBackend[F] {}
  def apply[F[_], S](delegate: StreamBackend[F, S], clientNonceGenerator: () => String): StreamBackend[F, S] =
    new DigestAuthenticationBackend(delegate, clientNonceGenerator) with StreamBackend[F, S] {}
  def apply[F[_], S](
      delegate: WebSocketStreamBackend[F, S],
      clientNonceGenerator: () => String
  ): WebSocketStreamBackend[F, S] =
    new DigestAuthenticationBackend(delegate, clientNonceGenerator) with WebSocketStreamBackend[F, S] {}

  private[client4] val DigestAuthTag = "__sttp_DigestAuth"
  private[client4] val ProxyDigestAuthTag = "__sttp_ProxyDigestAuth"
}
