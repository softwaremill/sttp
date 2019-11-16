package sttp.client

import sttp.client
import sttp.client.DigestAuthenticationBackend._
import sttp.client.DigestAuthenticator.DigestAuthData
import sttp.client.monad.MonadError
import sttp.client.monad.syntax._
import sttp.client.ws.WebSocketResponse

import scala.language.higherKinds

//TODO: support auth-int
//TODO: support stale param
//TODO: auth-info
class DigestAuthenticationBackend[F[_], S, WS_HANDLER[_]](delegate: SttpBackend[F, S, WS_HANDLER])
    extends SttpBackend[F, S, WS_HANDLER] {
  override def send[T](request: Request[T, S]): F[Response[T]] = {
    if (request.tag(DigestAuthTag).isDefined) {
      val digestAuthData = request.tag(DigestAuthTag).get.asInstanceOf[DigestAuthData]
      implicit val m: MonadError[F] = responseMonad
      delegate.send(request).flatMap { response =>
        val header = new client.DigestAuthenticator(digestAuthData).authenticate(request, response)
        header.map(h => delegate.send(request.header(h))).getOrElse(response.unit)
      }
    } else {
      delegate.send(request)
    }
  }

  override def openWebsocket[T, WS_RESULT](
      request: Request[T, S],
      handler: WS_HANDLER[WS_RESULT]
  ): F[WebSocketResponse[WS_RESULT]] = delegate.openWebsocket(request, handler)

  override def close(): F[Unit] = delegate.close()
  override def responseMonad: MonadError[F] = delegate.responseMonad
}

object DigestAuthenticationBackend {
  val DigestAuthTag = "__sttp_DigestAuth"

  implicit class DigestAuthRequest[U[_], T, S](requestT: RequestT[U, T, S]) {
    def digestAuth(username: String, password: String): RequestT[U, T, S] = {
      requestT.tag(DigestAuthTag, DigestAuthData(username, password))
    }
  }
}
