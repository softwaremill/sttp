package sttp.client.httpclient

import java.net.http.{HttpClient, HttpResponse}
import java.net.http.HttpResponse.BodyHandlers
import java.util.function.BiConsumer

import sttp.client
import sttp.client.{FollowRedirectsBackend, Request, Response, SttpBackend, SttpBackendOptions}
import sttp.client.monad.{FutureMonad, MonadAsyncError, MonadError}
import sttp.client.ws.WebSocketResponse

import scala.concurrent.{ExecutionContext, Future}

abstract class HttpClientAsyncBackend[F[_], S](client: HttpClient, monad: MonadAsyncError[F])
    extends HttpClientBackend[F, S](client) {
  override def send[T](request: Request[T, S]): F[Response[T]] = {
    val jRequest = convertRequest(request)

    monad.flatten(monad.async[F[Response[T]]] { cb: (Either[Throwable, F[Response[T]]] => Unit) =>
      def success(r: F[Response[T]]): Unit = cb(Right(r))
      def error(t: Throwable): Unit = cb(Left(t))

      client
        .sendAsync(jRequest, BodyHandlers.ofByteArray())
        .whenComplete(new BiConsumer[HttpResponse[Array[Byte]], Throwable] {
          override def accept(t: HttpResponse[Array[Byte]], u: Throwable): Unit = {
            if (t != null) {
              try success(readResponse(t, request.response))
              catch { case e: Exception => error(e) }
            }
            if (u != null) {
              error(u)
            }
          }
        })
      ()
    })
  }

  override def responseMonad: MonadError[F] = monad
}

class HttpClientFutureBackend private (client: HttpClient)(implicit ec: ExecutionContext)
    extends HttpClientAsyncBackend[Future, Nothing](client, new FutureMonad)

object HttpClientFutureBackend {
  private def apply(
      client: HttpClient
  )(implicit ec: ExecutionContext): SttpBackend[Future, Nothing, WebSocketResponse] =
    new FollowRedirectsBackend[Future, Nothing, WebSocketResponse](new HttpClientFutureBackend(client))

  def apply(options: SttpBackendOptions = SttpBackendOptions.Default)(
      implicit ec: ExecutionContext = ExecutionContext.Implicits.global
  ): SttpBackend[Future, Nothing, WebSocketResponse] =
    HttpClientFutureBackend(HttpBackend.defaultClient(client.DefaultReadTimeout.toMillis, options))

  def usingClient(client: HttpClient)(
      implicit ec: ExecutionContext = ExecutionContext.Implicits.global
  ): SttpBackend[Future, Nothing, WebSocketResponse] =
    HttpClientFutureBackend(client)
}
