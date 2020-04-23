package sttp.client

import sttp.client.DigestAuthenticationBackend._
import sttp.client.internal.DigestAuthenticator
import sttp.client.internal.DigestAuthenticator.DigestAuthData
import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
import sttp.client.ws.WebSocketResponse
import sttp.model.Header

import scala.language.higherKinds

class DigestAuthenticationBackend[F[_], S, WS_HANDLER[_]](
    delegate: SttpBackend[F, S, WS_HANDLER],
    clientNonceGenerator: () => String = DigestAuthenticator.defaultClientNonceGenerator
) extends SttpBackend[F, S, WS_HANDLER] {
  private implicit val m: MonadError[F] = responseMonad

  override def send[T](request: Request[T, S]): F[Response[T]] = {
    delegate
      .send(request)
      .flatMap { firstResponse =>
        handleResponse(request, firstResponse, ProxyDigestAuthTag, DigestAuthenticator.proxy(_, clientNonceGenerator))
      }
      .flatMap {
        case (secondResponse, proxyAuthHeader) =>
          handleResponse(
            proxyAuthHeader.map(h => request.header(h)).getOrElse(request),
            secondResponse,
            DigestAuthTag,
            DigestAuthenticator.apply(_, clientNonceGenerator)
          ).map(_._1)
      }
  }

  private def handleResponse[T](
      request: Request[T, S],
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

  override def openWebsocket[T, WS_RESULT](
      request: Request[T, S],
      handler: WS_HANDLER[WS_RESULT]
  ): F[WebSocketResponse[WS_RESULT]] = delegate.openWebsocket(request, handler)

  override def close(): F[Unit] = delegate.close()
  override def responseMonad: MonadError[F] = delegate.responseMonad
}

object DigestAuthenticationBackend {
  private[client] val DigestAuthTag = "__sttp_DigestAuth"
  private[client] val ProxyDigestAuthTag = "__sttp_ProxyDigestAuth"
}
