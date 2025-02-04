package sttp.client4.httpclient

import sttp.capabilities.Streams
import sttp.capabilities.WebSockets
import sttp.client4.GenericRequest
import sttp.client4.Response
import sttp.client4.WebSocketBackend
import sttp.client4.compression.CompressionHandlers
import sttp.client4.internal.SttpToJavaConverters.toJavaBiConsumer
import sttp.client4.internal.SttpToJavaConverters.toJavaFunction
import sttp.client4.internal.httpclient.AddToQueueListener
import sttp.client4.internal.httpclient.DelegatingWebSocketListener
import sttp.client4.internal.httpclient.Sequencer
import sttp.client4.internal.httpclient.WebSocketImpl
import sttp.client4.internal.ws.SimpleQueue
import sttp.client4.internal.ws.WebSocketEvent
import sttp.model.StatusCode
import sttp.monad.Canceler
import sttp.monad.MonadAsyncError
import sttp.monad.syntax._

import java.net.http._
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiConsumer
import sttp.model.ResponseMetadata

/** @tparam F
  *   The effect type
  * @tparam S
  *   Type of supported byte streams, `Nothing` if none
  * @tparam BH
  *   The low-level type of the body, read using a [[HttpResponse.BodyHandler]] read by [[HttpClient]].
  * @tparam B
  *   The higher-level body to which `BH` is transformed (e.g. a backend-native stream representation), which then is
  *   used to read the body as described by `responseAs`.
  */
abstract class HttpClientAsyncBackend[F[_], S <: Streams[S], BH, B](
    client: HttpClient,
    override implicit val monad: MonadAsyncError[F],
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    compressionHandlers: CompressionHandlers[S, B]
) extends HttpClientBackend[F, S, S with WebSockets, B](client, closeClient, compressionHandlers)
    with WebSocketBackend[F] {
  protected def createSimpleQueue[T]: F[SimpleQueue[F, T]]
  protected def createSequencer: F[Sequencer[F]]
  protected def createBodyHandler: HttpResponse.BodyHandler[BH]
  protected def lowLevelBodyToBody(p: BH): B
  protected def cancelLowLevelBody(p: BH): Unit
  protected def bodyToLimitedBody(b: B, limit: Long): B
  protected def emptyBody(): B

  /** A variant of [[MonadAsyncError.ensure]] which runs the finalizer only when the effect finished abnormally
    * (exception thrown, failed effect, cancellation/interruption).
    *
    * This is used to release any resources allocated by HttpClient after the request is sent, but before the response
    * is consumed. This is done only in case of failure, as in case of success the body is either fully consumed as
    * specified in the response description, or when an `...Unsafe` response description is used, it's up to the user to
    * consume it.
    *
    * Any exceptions that occur while running `finalizer` should be added as suppressed or logged, not impacting the
    * outcome of `effect`. If possible, `finalizer` should not be run in a cancellable way.
    */
  protected def ensureOnAbnormal[T](effect: F[T])(finalizer: => F[Unit]): F[T]

  override def sendRegular[T](request: GenericRequest[T, R]): F[Response[T]] = {
    monad
      .flatMap(convertRequest(request)) { convertedRequest =>
        val jRequest = customizeRequest(convertedRequest)

        // #1987: whenever the low-level body is acquired, we need to ensure any resources associated with it are
        // released. This includes proper handling of cancellation of the effect right after sending, but before
        // consuming the body (which happens only when the effect returned by `readResponse` is evaluated).

        // storing the low-level body (usually an `InputStream` or a `Publisher`) so that it can be cancelled;
        // cancellation might happen during request sending, so it's not always set
        val lowLevelBody = new AtomicReference[BH]()
        ensureOnAbnormal {
          monad
            .async[HttpResponse[BH]] { cb =>
              def success(r: HttpResponse[BH]): Unit = cb(Right(r))
              def error(t: Throwable): Unit = cb(Left(t))
              val cf = client.sendAsync(jRequest, createBodyHandler)

              cf.whenComplete(toJavaBiConsumer { (t: HttpResponse[BH], u: Throwable) =>
                if (t != null) {
                  lowLevelBody.set(t.body())
                  success(t)
                }
                if (u != null) {
                  error(u)
                }
              })

              // contrary to what the JavaDoc says, this actually cancels the request, even if it's in progress
              // however, the request will be cancelled asynchronously, and there's no way of waiting for cancellation to
              // complete; that's not ideal (both ZIO and cats-effect contracts require that the effect completes only
              // when cancellation is complete), but it's the best we can do
              // see: https://bugs.openjdk.org/browse/JDK-8245462
              Canceler { () =>
                val _ = cf.cancel(true)
              }
            }
            .flatMap { jResponse =>
              // sometimes body returned by HttpClient can be null, we handle this by returning empty body to prevent NPE
              val body = Option(jResponse.body())
                .map(lowLevelBodyToBody)
                .getOrElse(emptyBody())

              val limitedBody = request.options.maxResponseBodyLength.fold(body)(bodyToLimitedBody(body, _))

              readResponse(jResponse, Left(limitedBody), request)
            }
        } {
          monad.eval {
            // the request might have been interrupted during sending (no publisher is available then), or any time
            // after that, including right after the sending effect completed, but before the response was read
            val llb = lowLevelBody.get()
            if (llb != null) cancelLowLevelBody(llb)
          }
        }
      }
  }

  override def sendWebSocket[T](request: GenericRequest[T, R]): F[Response[T]] =
    (for {
      queue <- createSimpleQueue[WebSocketEvent]
      sequencer <- createSequencer
      ws <- sendWebSocket(request, queue, sequencer)
    } yield ws).handleError {
      case e: CompletionException if e.getCause.isInstanceOf[WebSocketHandshakeException] =>
        readResponse(
          e.getCause.asInstanceOf[WebSocketHandshakeException].getResponse,
          Left(emptyBody()),
          request
        )
    }

  private def sendWebSocket[T](
      request: GenericRequest[T, R],
      queue: SimpleQueue[F, WebSocketEvent],
      sequencer: Sequencer[F]
  ): F[Response[T]] = {
    val isOpen: AtomicBoolean = new AtomicBoolean(false)

    // see sendRegular for explanation
    val lowLevelWS = new AtomicReference[WebSocket]()
    ensureOnAbnormal {
      monad.flatten(monad.async[F[Response[T]]] { cb =>
        def success(r: F[Response[T]]): Unit = cb(Right(r))
        def error(t: Throwable): Unit = cb(Left(t))

        val listener = new DelegatingWebSocketListener(
          new AddToQueueListener(queue, isOpen),
          ws => {
            lowLevelWS.set(ws)
            val webSocket = new WebSocketImpl[F](
              ws,
              queue,
              isOpen,
              sequencer,
              monad,
              cf =>
                monad.async { cb =>
                  cf.whenComplete(new BiConsumer[WebSocket, Throwable] {
                    override def accept(t: WebSocket, error: Throwable): Unit =
                      if (error != null) {
                        cb(Left(error))
                      } else {
                        cb(Right(()))
                      }
                  })
                  Canceler { () =>
                    val _ = cf.cancel(true)
                  }
                }
            )
            val baseResponse = Response((), StatusCode.SwitchingProtocols, "", Nil, Nil, request.onlyMetadata)
            val body = bodyFromHttpClient(Right(webSocket), request.response, baseResponse)
            success(body.map(b => baseResponse.copy(body = b)))
          },
          error
        )

        val cf = prepareWebSocketBuilder(request, client)
          .buildAsync(request.uri.toJavaUri, listener)
          .thenApply[Unit](toJavaFunction((_: WebSocket) => ()))
          .exceptionally(toJavaFunction((t: Throwable) => cb(Left(t))))
        Canceler { () =>
          val _ = cf.cancel(true)
        }
      })
    } {
      val llws = lowLevelWS.get()
      if (llws != null) monad.eval(llws.abort()) else monad.unit(())
    }
  }
}
