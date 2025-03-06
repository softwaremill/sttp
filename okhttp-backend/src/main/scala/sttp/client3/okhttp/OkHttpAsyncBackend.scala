package sttp.client3.okhttp

import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

import okhttp3.{Call, Callback, OkHttpClient, Response => OkHttpResponse, WebSocket => OkHttpWebSocket}
import sttp.capabilities.Streams
import sttp.client3.internal.ws.{SimpleQueue, WebSocketEvent}
import sttp.monad.syntax._
import sttp.client3.okhttp.OkHttpBackend.EncodingHandler
import sttp.client3.{Request, Response, ignore}
import sttp.monad.{Canceler, MonadAsyncError}
import java.util.concurrent.atomic.AtomicReference

abstract class OkHttpAsyncBackend[F[_], S <: Streams[S], P](
    client: OkHttpClient,
    monad: MonadAsyncError[F],
    closeClient: Boolean,
    customEncodingHandler: EncodingHandler
) extends OkHttpBackend[F, S, P](client, closeClient, customEncodingHandler) {

  // #1987: see the comments in HttpClientAsyncBackend
  protected def ensureOnAbnormal[T](effect: F[T])(finalizer: => F[Unit]): F[T]

  override protected def sendRegular[T, R >: PE](request: Request[T, R]): F[Response[T]] = {
    val nativeRequest = convertRequest(request)
    val okHttpResponse = new AtomicReference[OkHttpResponse]()
    ensureOnAbnormal {
      monad.flatten(monad.async[F[Response[T]]] { cb =>
        def success(r: F[Response[T]]): Unit = cb(Right(r))

        def error(t: Throwable): Unit = cb(Left(t))

        val call = OkHttpBackend
          .updateClientIfCustomReadTimeout(request, client)
          .newCall(nativeRequest)

        call.enqueue(new Callback {
          override def onFailure(call: Call, e: IOException): Unit =
            error(e)

          override def onResponse(call: Call, response: OkHttpResponse): Unit = {
            okHttpResponse.set(response)
            try success(readResponse(response, request))
            catch {
              case e: Exception =>
                try response.close()
                finally error(e)
            }
          }
        })

        Canceler(() => call.cancel())
      })
    } {
      monad.eval {
        val response = okHttpResponse.get()
        if (response != null) response.close()
      }
    }
  }

  override protected def sendWebSocket[T, R >: PE](
      request: Request[T, R]
  ): F[Response[T]] = {
    val nativeRequest = convertRequest(request)
    val okHttpWS = new AtomicReference[okhttp3.WebSocket]()
    ensureOnAbnormal {
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
    } {
      monad.eval {
        val ws = okHttpWS.get()
        if (ws != null) ws.cancel()
      }
    }
  }

  private def createListener[R >: PE, T](
      queue: SimpleQueue[F, WebSocketEvent],
      cb: Either[Throwable, F[Response[T]]] => Unit,
      request: Request[T, R]
  ): DelegatingWebSocketListener = {
    val isOpen = new AtomicBoolean(false)
    val addToQueue = new AddToQueueListener(queue, isOpen)

    def onOpen(nativeWs: OkHttpWebSocket, response: OkHttpResponse) = {
      val webSocket = new WebSocketImpl(nativeWs, queue, isOpen, response.headers())
      val wsResponse = readResponse(response, request.response(ignore))
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

  override implicit val responseMonad: MonadAsyncError[F] = monad
}
