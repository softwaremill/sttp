package sttp.client.impl.scalaz

import scalaz.~>
import sttp.client.monad.MonadError
import sttp.client.ws.WebSocketResponse
import sttp.client.{Request, Response, SttpBackend}

import scala.language.higherKinds

object implicits extends ScalazImplicits

trait ScalazImplicits {
  implicit final def sttpBackendToScalazMappableSttpBackend[F[_], S, WS_HANDLER[_]](
      sttpBackend: SttpBackend[F, S, WS_HANDLER]
  ): MappableSttpBackend[F, S, WS_HANDLER] = new MappableSttpBackend(sttpBackend)
}

final class MappableSttpBackend[F[_], S, WS_HANDLER[_]] private[scalaz] (
    private val sttpBackend: SttpBackend[F, S, WS_HANDLER]
) extends AnyVal {
  def mapK[G[_]: MonadError](f: F ~> G): SttpBackend[G, S, WS_HANDLER] =
    new MappedKSttpBackend(sttpBackend, f, implicitly)
}

private[scalaz] final class MappedKSttpBackend[F[_], -S, WS_HANDLER[_], G[_]](
    wrapped: SttpBackend[F, S, WS_HANDLER],
    mapping: F ~> G,
    val responseMonad: MonadError[G]
) extends SttpBackend[G, S, WS_HANDLER] {
  def send[T](request: Request[T, S]): G[Response[T]] = mapping(wrapped.send(request))

  override def openWebsocket[T, WS_RESULT](
      request: Request[T, S],
      handler: WS_HANDLER[WS_RESULT]
  ): G[WebSocketResponse[WS_RESULT]] = mapping(wrapped.openWebsocket(request, handler))

  def close(): G[Unit] = mapping(wrapped.close())
}
