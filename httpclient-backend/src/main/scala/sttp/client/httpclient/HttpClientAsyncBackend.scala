package sttp.client.httpclient

import java.io.InputStream
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.function.BiConsumer

import sttp.client.httpclient.HttpClientBackend.EncodingHandler
import sttp.client.monad.{Canceler, FutureMonad, MonadAsyncError, MonadError}
import sttp.client.testing.SttpBackendStub
import sttp.client.ws.WebSocketResponse
import sttp.client.{FollowRedirectsBackend, Request, Response, SttpBackend, SttpBackendOptions, SttpClientException}
import sttp.model.Headers

import scala.concurrent.{ExecutionContext, Future}

abstract class HttpClientAsyncBackend[F[_], S](
    client: HttpClient,
    monad: MonadAsyncError[F],
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: EncodingHandler
) extends HttpClientBackend[F, S](client, closeClient, customEncodingHandler) {
  override def send[T](request: Request[T, S]): F[Response[T]] =
    adjustExceptions {
      val jRequest = customizeRequest(convertRequest(request))

      monad.flatten(monad.async[F[Response[T]]] { (cb: (Either[Throwable, F[Response[T]]] => Unit)) =>
        def success(r: F[Response[T]]): Unit = cb(Right(r))
        def error(t: Throwable): Unit = cb(Left(t))

        val cf = client
          .sendAsync(jRequest, BodyHandlers.ofInputStream())
          .whenComplete(new BiConsumer[HttpResponse[InputStream], Throwable] {
            override def accept(t: HttpResponse[InputStream], u: Throwable): Unit = {
              if (t != null) {
                try success(readResponse(t, request.response))
                catch { case e: Exception => error(e) }
              }
              if (u != null) {
                error(u)
              }
            }
          })
        Canceler(() => cf.cancel(true))
      })
    }

  override def openWebsocket[T, WS_RESULT](
      request: Request[T, S],
      handler: WebSocketHandler[WS_RESULT]
  ): F[WebSocketResponse[WS_RESULT]] =
    adjustExceptions {
      monad.flatten(monad.async[F[WebSocketResponse[WS_RESULT]]] { cb =>
        def success(r: F[WebSocketResponse[WS_RESULT]]): Unit = cb(Right(r))
        def error(t: Throwable): Unit = cb(Left(t))

        val listener = new DelegatingWebSocketListener(
          handler.listener,
          webSocket => {
            val wsResponse =
              monad.unit(sttp.client.ws.WebSocketResponse(Headers.apply(Seq.empty), handler.createResult(webSocket)))
            success(wsResponse)
          },
          error
        )

        val wsBuilder = client.newWebSocketBuilder()
        client.connectTimeout().map(wsBuilder.connectTimeout(_))
        request.headers.foreach(h => wsBuilder.header(h.name, h.value))
        val cf = wsBuilder
          .buildAsync(request.uri.toJavaUri, listener)
          .thenApply(_ => ())
          .exceptionally(t => cb(Left(t)))
        Canceler(() => cf.cancel(true))
      })
    }

  private def adjustExceptions[T](t: => F[T]): F[T] =
    SttpClientException.adjustExceptions(responseMonad)(t)(SttpClientException.defaultExceptionToSttpClientException)

  override def responseMonad: MonadError[F] = monad
}

class HttpClientFutureBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: EncodingHandler
)(implicit ec: ExecutionContext)
    extends HttpClientAsyncBackend[Future, Nothing](
      client,
      new FutureMonad,
      closeClient,
      customizeRequest,
      customEncodingHandler
    )

object HttpClientFutureBackend {
  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      customEncodingHandler: EncodingHandler
  )(implicit ec: ExecutionContext): SttpBackend[Future, Nothing, WebSocketHandler] =
    new FollowRedirectsBackend[Future, Nothing, WebSocketHandler](
      new HttpClientFutureBackend(client, closeClient, customizeRequest, customEncodingHandler)
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  )(implicit
      ec: ExecutionContext = ExecutionContext.global
  ): SttpBackend[Future, Nothing, WebSocketHandler] =
    HttpClientFutureBackend(
      HttpClientBackend.defaultClient(options),
      closeClient = true,
      customizeRequest,
      customEncodingHandler
    )

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  )(implicit
      ec: ExecutionContext = ExecutionContext.global
  ): SttpBackend[Future, Nothing, WebSocketHandler] =
    HttpClientFutureBackend(client, closeClient = false, customizeRequest, customEncodingHandler)

  /**
    * Create a stub backend for testing, which uses the [[Future]] response wrapper, and doesn't support streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub(implicit ec: ExecutionContext = ExecutionContext.global): SttpBackendStub[Future, Nothing] =
    SttpBackendStub(new FutureMonad())
}
