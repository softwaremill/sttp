package sttp.client4.okhttp

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

import okhttp3.{Call, Callback, OkHttpClient, Response => OkHttpResponse, WebSocket => OkHttpWebSocket}
import sttp.capabilities.Streams
import sttp.client4.internal.ws.{SimpleQueue, WebSocketEvent}
import sttp.monad.syntax._
import sttp.client4.{ignore, GenericRequest, Response}
import sttp.monad.{Canceler, MonadAsyncError}
import sttp.client4.compression.CompressionHandlers
import java.io.InputStream

abstract class OkHttpAsyncBackend[F[_], S <: Streams[S], P](
    client: OkHttpClient,
    _monad: MonadAsyncError[F],
    closeClient: Boolean,
    compressionHandlers: CompressionHandlers[P, InputStream]
) extends OkHttpBackend[F, S, P](client, closeClient, compressionHandlers) {

  override protected def sendRegular[T](request: GenericRequest[T, R]): F[Response[T]] = {
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
          try success(readResponse(response, request, request.response))
          catch {
            case e: Exception =>
              response.close()
              error(e)
          }
      })

      Canceler(() => call.cancel())
    })
  }

  override protected def sendWebSocket[T](
      request: GenericRequest[T, R]
  ): F[Response[T]] = {
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

  private def createListener[T](
      queue: SimpleQueue[F, WebSocketEvent],
      cb: Either[Throwable, F[Response[T]]] => Unit,
      request: GenericRequest[T, R]
  ): DelegatingWebSocketListener = {
    val isOpen = new AtomicBoolean(false)
    val addToQueue = new AddToQueueListener(queue, isOpen)

    def onOpen(nativeWs: OkHttpWebSocket, response: OkHttpResponse) = {
      val webSocket = new WebSocketImpl(nativeWs, queue, isOpen, response.headers())
      val wsResponse = readResponse(response, request, ignore)
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

  override implicit val monad: MonadAsyncError[F] = _monad
}
