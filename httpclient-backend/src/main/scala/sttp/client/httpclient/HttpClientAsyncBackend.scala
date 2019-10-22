package sttp.client.httpclient

import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpResponse}
import java.util.function.BiConsumer

import sttp.client.monad.{FutureMonad, MonadAsyncError, MonadError}
import sttp.client.ws.WebSocketResponse
import sttp.client.{FollowRedirectsBackend, Request, Response, SttpBackend, SttpBackendOptions}
import sttp.model.Headers

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

  override def openWebsocket[T, WS_RESULT](
      request: Request[T, S],
      handler: WebSocketHandler[WS_RESULT]
  ): F[WebSocketResponse[WS_RESULT]] = {
    monad.flatten(monad.async[F[WebSocketResponse[WS_RESULT]]] { cb =>
      def success(r: F[WebSocketResponse[WS_RESULT]]): Unit = cb(Right(r))
      def error(t: Throwable): Unit = cb(Left(t))

      val listener = new DelegatingWebSocketListener(
        handler.listener,
        webSocket => {
          val wsResponse =
            monad.unit(sttp.client.ws.WebSocketResponse(Headers.apply(Seq.empty), handler.wrIsWebSocket(webSocket)))
          success(wsResponse)
        },
        error,
        handler.wrIsWebSocket
      )

      val _ = HttpClient
        .newHttpClient()
        .newWebSocketBuilder()
        .buildAsync(request.uri.toJavaUri, listener)
    })
  }

  override def responseMonad: MonadError[F] = monad
}

class HttpClientFutureBackend private (client: HttpClient)(implicit ec: ExecutionContext)
    extends HttpClientAsyncBackend[Future, Nothing](client, new FutureMonad)

object HttpClientFutureBackend {
  private def apply(
      client: HttpClient
  )(implicit ec: ExecutionContext): SttpBackend[Future, Nothing, WebSocketHandler] =
    new FollowRedirectsBackend[Future, Nothing, WebSocketHandler](new HttpClientFutureBackend(client))

  def apply(options: SttpBackendOptions = SttpBackendOptions.Default)(
      implicit ec: ExecutionContext = ExecutionContext.Implicits.global
  ): SttpBackend[Future, Nothing, WebSocketHandler] =
    HttpClientFutureBackend(HttpBackend.defaultClient(options))

  def usingClient(client: HttpClient)(
      implicit ec: ExecutionContext = ExecutionContext.Implicits.global
  ): SttpBackend[Future, Nothing, WebSocketHandler] =
    HttpClientFutureBackend(client)
}
