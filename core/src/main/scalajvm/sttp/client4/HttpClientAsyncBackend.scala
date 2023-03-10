package sttp.client4

import sttp.capabilities.WebSockets
import sttp.client4.HttpClientBackend.EncodingHandler
import sttp.client4.internal.SttpToJavaConverters.{toJavaBiConsumer, toJavaFunction}
import sttp.client4.internal.httpclient.{AddToQueueListener, DelegatingWebSocketListener, Sequencer, WebSocketImpl}
import sttp.client4.internal.ws.{SimpleQueue, WebSocketEvent}
import sttp.model.{HeaderNames, StatusCode}
import sttp.monad.syntax._
import sttp.monad.{Canceler, MonadAsyncError, MonadError}

import java.net.http._
import java.time.Duration
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicBoolean

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
abstract class HttpClientAsyncBackend[F[_], S, BH, B](
    client: HttpClient,
    private implicit val monad: MonadAsyncError[F],
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: EncodingHandler[B]
) extends HttpClientBackend[F, S, S with WebSockets, B](client, closeClient, customEncodingHandler)
    with WebSocketBackend[F] {

  override def send[T](request: GenericRequest[T, R]): F[Response[T]] =
    adjustExceptions(request) {
      if (request.isWebSocket) sendWebSocket(request) else sendRegular(request)
    }

  protected def createBodyHandler: HttpResponse.BodyHandler[BH]
  protected def createSimpleQueue[T]: F[SimpleQueue[F, T]]
  protected def createSequencer: F[Sequencer[F]]
  protected def bodyHandlerBodyToBody(p: BH): B
  protected def emptyBody(): B

  private def sendRegular[T](request: GenericRequest[T, R]): F[Response[T]] = {
    monad.flatMap(convertRequest(request)) { convertedRequest =>
      val jRequest = customizeRequest(convertedRequest)

      monad.flatten(monad.async[F[Response[T]]] { cb =>
        def success(r: F[Response[T]]): Unit = cb(Right(r))
        def error(t: Throwable): Unit = cb(Left(t))
        var cf = client.sendAsync(jRequest, createBodyHandler)

        val consumer = toJavaBiConsumer((t: HttpResponse[BH], u: Throwable) => {
          if (t != null) {
            try success(readResponse(t, Left(bodyHandlerBodyToBody(t.body())), request))
            catch {
              case e: Exception => error(e)
            }
          }
          if (u != null) {
            error(u)
          }
        })

        cf = client.executor().orElse(null) match {
          case null => cf.whenComplete(consumer)
          case e    => cf.whenCompleteAsync(consumer, e) // using the provided executor to further process the body
        }

        Canceler(() => cf.cancel(true))
      })
    }
  }

  private def sendWebSocket[T](request: GenericRequest[T, R]): F[Response[T]] = {
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
          val webSocket = new WebSocketImpl[F](ws, queue, isOpen, monad, sequencer)
          val baseResponse = Response((), StatusCode.SwitchingProtocols, "", Nil, Nil, request.onlyMetadata)
          val body = bodyFromHttpClient(Right(webSocket), request.response, baseResponse)
          success(body.map(b => baseResponse.copy(body = b)))
        },
        error
      )

      val wsSubProtocols = request.headers
        .find(_.is(HeaderNames.SecWebSocketProtocol))
        .map(_.value)
        .toSeq
        .flatMap(_.split(","))
        .map(_.trim)
        .toList
      val wsBuilder = wsSubProtocols match {
        case Nil          => client.newWebSocketBuilder()
        case head :: Nil  => client.newWebSocketBuilder().subprotocols(head)
        case head :: tail => client.newWebSocketBuilder().subprotocols(head, tail: _*)
      }
      client
        .connectTimeout()
        .map[java.net.http.WebSocket.Builder](toJavaFunction((d: Duration) => wsBuilder.connectTimeout(d)))
      filterIllegalWsHeaders(request).headers.foreach(h => wsBuilder.header(h.name, h.value))
      val cf = wsBuilder
        .buildAsync(request.uri.toJavaUri, listener)
        .thenApply[Unit](toJavaFunction((_: WebSocket) => ()))
        .exceptionally(toJavaFunction((t: Throwable) => cb(Left(t))))
      Canceler(() => cf.cancel(true))
    })
  }

  private def filterIllegalWsHeaders[T](request: GenericRequest[T, R]): GenericRequest[T, R] = {
    request.withHeaders(request.headers.filter(h => !wsIllegalHeaders.contains(h.name.toLowerCase)))
  }

  private def adjustExceptions[T](request: GenericRequest[_, _])(t: => F[T]): F[T] =
    SttpClientException.adjustExceptions(responseMonad)(t)(
      SttpClientException.defaultExceptionToSttpClientException(request, _)
    )

  override def responseMonad: MonadError[F] = monad

  // these headers can't be sent using HttpClient; the SecWebSocketProtocol is supported through a builder method,
  // the resit is ignored
  private val wsIllegalHeaders: Set[String] = {
    import HeaderNames._
    Set(SecWebSocketAccept, SecWebSocketExtensions, SecWebSocketKey, SecWebSocketVersion, SecWebSocketProtocol).map(
      _.toLowerCase
    )
  }
}
