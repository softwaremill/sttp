package sttp.client.okhttp

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

import okhttp3.{Call, Callback, OkHttpClient, Response => OkHttpResponse, WebSocket => OkHttpWebSocket}
import sttp.client.monad.syntax._
import sttp.client.monad.{Canceler, MonadAsyncError, MonadError}
import sttp.client.okhttp.OkHttpBackend.EncodingHandler
import sttp.client.ws.internal.{SimpleQueue, WebSocketEvent}
import sttp.client.{Request, Response, Streams, ignore}

abstract class OkHttpAsyncBackend[F[_], S <: Streams[S], P](
    client: OkHttpClient,
    monad: MonadAsyncError[F],
    closeClient: Boolean,
    customEncodingHandler: EncodingHandler
) extends OkHttpBackend[F, S, P](client, closeClient, customEncodingHandler) {

  override protected def sendRegular[T, R >: PE](request: Request[T, R]): F[Response[T]] = {
    val nativeRequest = convertRequest(request)
    monad.flatten(monad.async[F[Response[T]]] { cb =>
      def success(r: F[Response[T]]): Unit = cb(Right(r))

      def error(t: Throwable): Unit = cb(Left(t))

      val call = OkHttpBackend
        .updateClientIfCustomReadTimeout(request, client)
        .newCall(nativeRequest)

      call.enqueue(new Callback {
        override def onFailure(call: Call, e: IOException): Unit =
          error(e)

        override def onResponse(call: Call, response: OkHttpResponse): Unit =
          try success(readResponse(response, request.response))
          catch {
            case e: Exception =>
              response.close()
              error(e)
          }
      })

      Canceler(() => call.cancel())
    })
  }

  override protected def sendWebSocket[T, R >: PE](
      request: Request[T, R]
  ): F[Response[T]] = {
    implicit val m = monad
    val nativeRequest = convertRequest(request)
    monad.flatten(
      createSimpleQueue[WebSocketEvent]
        .flatMap { queue =>
          monad.async[F[Response[T]]] { cb =>
            val listener = createListener(queue, cb, request)
            val ws = OkHttpBackend
              .updateClientIfCustomReadTimeout(request, client)
              .newWebSocket(nativeRequest, listener)

            Canceler(() => ws.cancel())
          }
        }
    )
  }

  private def createListener[R >: PE, T](
      queue: SimpleQueue[F, WebSocketEvent],
      cb: Either[Throwable, F[Response[T]]] => Unit,
      request: Request[T, R]
  )(implicit m: MonadAsyncError[F]): DelegatingWebSocketListener = {
    val isOpen = new AtomicBoolean(false)
    val addToQueue = new AddToQueueListener(queue, isOpen)

    def onOpen(nativeWs: OkHttpWebSocket, response: OkHttpResponse) = {
      val webSocket = new WebSocketImpl(nativeWs, queue, isOpen)
      val wsResponse = readResponse(response, ignore)
        .flatMap { baseResponse =>
          bodyFromOkHttp(
            response.body().byteStream(),
            request.response,
            baseResponse,
            Some(webSocket)
          ).map(b => baseResponse.copy(body = b))
        }
      cb(Right(wsResponse))
    }
    def onError(e: Throwable) = cb(Left(e))

    new DelegatingWebSocketListener(
      addToQueue,
      onOpen,
      onError
    )
  }

  override def responseMonad: MonadError[F] = monad
}
