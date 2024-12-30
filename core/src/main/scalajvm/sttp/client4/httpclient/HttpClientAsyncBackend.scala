package sttp.client4.httpclient

import sttp.capabilities.{Streams, WebSockets}
import sttp.client4.internal.SttpToJavaConverters.{toJavaBiConsumer, toJavaFunction}
import sttp.client4.internal.httpclient.{AddToQueueListener, DelegatingWebSocketListener, Sequencer, WebSocketImpl}
import sttp.client4.internal.ws.{SimpleQueue, WebSocketEvent}
import sttp.client4.{GenericRequest, Response, WebSocketBackend}
import sttp.client4.compression.CompressionHandlers
import sttp.model.StatusCode
import sttp.monad.syntax._
import sttp.monad.{Canceler, MonadAsyncError}

import java.net.http._
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiConsumer

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
  protected def bodyHandlerBodyToBody(p: BH): B
  protected def emptyBody(): B

  override def sendRegular[T](request: GenericRequest[T, R]): F[Response[T]] =
    monad.flatMap(convertRequest(request)) { convertedRequest =>
      val jRequest = customizeRequest(convertedRequest)

      monad.flatten(monad.async[F[Response[T]]] { cb =>
        def success(r: F[Response[T]]): Unit = cb(Right(r))
        def error(t: Throwable): Unit = cb(Left(t))
        var cf = client.sendAsync(jRequest, createBodyHandler)

        val consumer = toJavaBiConsumer { (t: HttpResponse[BH], u: Throwable) =>
          if (t != null) {
            // sometimes body returned by HttpClient can be null, we handle this by returning empty body to prevent NPE
            val body = Option(t.body())
              .map(bodyHandlerBodyToBody)
              .getOrElse(emptyBody())

            try success(readResponse(t, Left(body), request))
            catch {
              case e: Exception => error(e)
            }
          }
          if (u != null) {
            error(u)
          }
        }

        cf = client.executor().orElse(null) match {
          case null => cf.whenComplete(consumer)
          case e    => cf.whenCompleteAsync(consumer, e) // using the provided executor to further process the body
        }

        Canceler(() => cf.cancel(true))
      })
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
    monad.flatten(monad.async[F[Response[T]]] { cb =>
      def success(r: F[Response[T]]): Unit = cb(Right(r))
      def error(t: Throwable): Unit = cb(Left(t))

      val listener = new DelegatingWebSocketListener(
        new AddToQueueListener(queue, isOpen),
        ws => {
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
                  cf.cancel(true)
                  ()
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
      Canceler(() => cf.cancel(true))
    })
  }
}
