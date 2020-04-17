package sttp.client.impl.cats

import cats.effect.Concurrent
import cats.~>
import sttp.client.monad.{MonadAsyncError, MonadError}
import sttp.client.ws.WebSocketResponse
import sttp.client.{Request, Response, SttpBackend}

import scala.language.higherKinds

object implicits extends CatsImplicits

trait CatsImplicits extends LowLevelCatsImplicits {
  implicit final def sttpBackendToCatsMappableSttpBackend[R[_], S, WS_HANDLER[_]](
      sttpBackend: SttpBackend[R, S, WS_HANDLER]
  ): MappableSttpBackend[R, S, WS_HANDLER] = new MappableSttpBackend(sttpBackend)

  implicit final def asyncMonadError[F[_]: Concurrent]: MonadAsyncError[F] = new CatsMonadAsyncError[F]
}

trait LowLevelCatsImplicits {
  implicit final def catsMonadError[F[_]](implicit E: cats.MonadError[F, Throwable]): MonadError[F] =
    new CatsMonadError[F]
}

final class MappableSttpBackend[F[_], S, WS_HANDLER[_]] private[cats] (
    private val sttpBackend: SttpBackend[F, S, WS_HANDLER]
) extends AnyVal {
  def mapK[G[_]: MonadError](f: F ~> G): SttpBackend[G, S, WS_HANDLER] =
    new MappedKSttpBackend(sttpBackend, f, implicitly)
}

private[cats] final class MappedKSttpBackend[F[_], -S, WS_HANDLER[_], G[_]](
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
