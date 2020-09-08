package sttp.client.httpclient

import java.io.InputStream
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse, WebSocketHandshakeException}
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicBoolean

import sttp.client.httpclient.HttpClientBackend.EncodingHandler
import sttp.client.internal.emptyInputStream
import sttp.client.internal.ws.{SimpleQueue, WebSocketEvent}
import sttp.monad.syntax._
import sttp.monad.{Canceler, MonadError}
import sttp.client.{Request, Response, SttpClientException}
import sttp.model.StatusCode
import sttp.monad.MonadAsyncError

abstract class HttpClientAsyncBackend[F[_], S, P](
    client: HttpClient,
    private implicit val monad: MonadAsyncError[F],
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: EncodingHandler
) extends HttpClientBackend[F, S, P](client, closeClient, customEncodingHandler) {
  override def send[T, R >: PE](request: Request[T, R]): F[Response[T]] =
    adjustExceptions(request) {
      if (request.isWebSocket) sendWebSocket(request) else sendRegular(request)
    }

  protected def createSimpleQueue[T]: F[SimpleQueue[F, T]]

  private def sendRegular[T, R >: PE](request: Request[T, R]): F[Response[T]] = {
    monad.flatMap(convertRequest(request)) { convertedRequest =>
      val jRequest = customizeRequest(convertedRequest)

      monad.flatten(monad.async[F[Response[T]]] { cb =>
        def success(r: F[Response[T]]): Unit = cb(Right(r))
        def error(t: Throwable): Unit = cb(Left(t))

        val cf = client
          .sendAsync(jRequest, BodyHandlers.ofInputStream())
          .whenComplete((t: HttpResponse[InputStream], u: Throwable) => {
            if (t != null) {
              try success(readResponse(t, Left(t.body()), request))
              catch {
                case e: Exception => error(e)
              }
            }
            if (u != null) {
              error(u)
            }
          })
        Canceler(() => cf.cancel(true))
      })
    }
  }

  private def sendWebSocket[T, R >: PE](request: Request[T, R]): F[Response[T]] = {
    createSimpleQueue[WebSocketEvent]
      .flatMap { queue =>
        val isOpen: AtomicBoolean = new AtomicBoolean(false)
        monad.flatten(monad.async[F[Response[T]]] { cb =>
          def success(r: F[Response[T]]): Unit = cb(Right(r))
          def error(t: Throwable): Unit = cb(Left(t))

          val listener = new DelegatingWebSocketListener(
            new AddToQueueListener(queue, isOpen),
            ws => {
              val webSocket = new WebSocketImpl[F](ws, queue, isOpen, monad)
              val baseResponse = Response((), StatusCode.SwitchingProtocols, "", Nil, Nil, request.onlyMetadata)
              val body = bodyFromHttpClient(
                Right(webSocket),
                request.response,
                baseResponse
              )
              success(body.map(b => baseResponse.copy(body = b)))
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
      .handleError {
        case e: CompletionException if e.getCause.isInstanceOf[WebSocketHandshakeException] =>
          readResponse(
            e.getCause.asInstanceOf[WebSocketHandshakeException].getResponse,
            Left(emptyInputStream()),
            request
          )
      }
  }

  private def adjustExceptions[T](request: Request[_, _])(t: => F[T]): F[T] =
    SttpClientException.adjustExceptions(responseMonad)(t)(
      SttpClientException.defaultExceptionToSttpClientException(request, _)
    )

  override def responseMonad: MonadError[F] = monad
}
