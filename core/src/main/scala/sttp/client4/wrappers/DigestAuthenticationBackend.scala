package sttp.client4.wrappers

import sttp.capabilities.Effect
import sttp.client4.internal.DigestAuthenticator
import sttp.client4.internal.DigestAuthenticator.DigestAuthData
import sttp.client4.wrappers.DigestAuthenticationBackend._
import sttp.client4.{
  Backend,
  GenericBackend,
  GenericRequest,
  Response,
  StreamBackend,
  SyncBackend,
  WebSocketBackend,
  WebSocketStreamBackend,
  WebSocketSyncBackend
}
import sttp.model.Header
import sttp.monad.syntax._
import sttp.attributes.AttributeKey

abstract class DigestAuthenticationBackend[F[_], P] private (
    delegate: GenericBackend[F, P],
    clientNonceGenerator: () => String
) extends DelegateBackend(delegate) {

  override def send[T](request: GenericRequest[T, P with Effect[F]]): F[Response[T]] =
    delegate
      .send(request)
      .flatMap { firstResponse =>
        handleResponse(
          request,
          firstResponse,
          ProxyDigestAuthAttributeKey,
          DigestAuthenticator.proxy(_, clientNonceGenerator)
        )
      }
      .flatMap { case (secondResponse, proxyAuthHeader) =>
        handleResponse(
          proxyAuthHeader.map(h => request.header(h)).getOrElse(request),
          secondResponse,
          DigestAuthAttributeKey,
          DigestAuthenticator.apply(_, clientNonceGenerator)
        ).map(_._1)
      }

  private def handleResponse[T](
      request: GenericRequest[T, P with Effect[F]],
      response: Response[T],
      digestAttributeKey: AttributeKey[DigestAuthenticator.DigestAuthData],
      digestAuthenticator: DigestAuthData => DigestAuthenticator
  ): F[(Response[T], Option[Header])] =
    request
      .attribute(digestAttributeKey)
      .flatMap { digestAuthData =>
        val header = digestAuthenticator(digestAuthData).authenticate(request, response)
        header.map(h => delegate.send(request.header(h)).map(_ -> Option(h)))
      }
      .getOrElse((response -> Option.empty[Header]).unit)
}

object DigestAuthenticationBackend {
  def apply(delegate: SyncBackend): SyncBackend = apply(delegate, DigestAuthenticator.defaultClientNonceGenerator _)
  def apply[F[_]](delegate: Backend[F]): Backend[F] = apply(delegate, DigestAuthenticator.defaultClientNonceGenerator _)
  def apply[F[_]](delegate: WebSocketBackend[F]): WebSocketBackend[F] =
    apply(delegate, DigestAuthenticator.defaultClientNonceGenerator _)
  def apply[F[_]](delegate: WebSocketSyncBackend): WebSocketSyncBackend =
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
  def apply(delegate: WebSocketSyncBackend, clientNonceGenerator: () => String): WebSocketSyncBackend =
    new DigestAuthenticationBackend(delegate, clientNonceGenerator) with WebSocketSyncBackend {}
  def apply[F[_], S](delegate: StreamBackend[F, S], clientNonceGenerator: () => String): StreamBackend[F, S] =
    new DigestAuthenticationBackend(delegate, clientNonceGenerator) with StreamBackend[F, S] {}
  def apply[F[_], S](
      delegate: WebSocketStreamBackend[F, S],
      clientNonceGenerator: () => String
  ): WebSocketStreamBackend[F, S] =
    new DigestAuthenticationBackend(delegate, clientNonceGenerator) with WebSocketStreamBackend[F, S] {}

  private[client4] val DigestAuthAttributeKey = new AttributeKey[DigestAuthenticator.DigestAuthData](
    "sttp.client4.internal.DigestAuthenticator.DigestAuthData.direct"
  )
  private[client4] val ProxyDigestAuthAttributeKey = new AttributeKey[DigestAuthenticator.DigestAuthData](
    "sttp.client4.internal.DigestAuthenticator.DigestAuthData.proxy"
  )
}
