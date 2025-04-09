package sttp.client3

import sttp.client3.HttpClientBackend.EncodingHandler
import sttp.client3.internal.SttpToJavaConverters.{toJavaBiConsumer, toJavaFunction}
import sttp.client3.internal.httpclient.{AddToQueueListener, DelegatingWebSocketListener, Sequencer, WebSocketImpl}
import sttp.client3.internal.ws.{SimpleQueue, WebSocketEvent}
import sttp.model.{HeaderNames, StatusCode}
import sttp.monad.syntax._
import sttp.monad.{Canceler, MonadAsyncError, MonadError}

import java.net.http._
import java.time.Duration
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** @tparam F
  *   The effect type
  * @tparam S
  *   Type of supported byte streams, `Nothing` if none
  * @tparam P
  *   Capabilities supported by the backend. See [[SttpBackend]].
  * @tparam BH
  *   The low-level type of the body, read using a [[HttpResponse.BodyHandler]] read by [[HttpClient]].
  * @tparam B
  *   The higher-level body to which `BH` is transformed (e.g. a backend-native stream representation), which then is
  *   used to read the body as described by `responseAs`.
  */
abstract class HttpClientAsyncBackend[F[_], S, P, BH, B](
    client: HttpClient,
    private implicit val monad: MonadAsyncError[F],
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: EncodingHandler[B]
) extends HttpClientBackend[F, S, P, B](client, closeClient, customEncodingHandler) {
  override def send[T, R >: PE](request: Request[T, R]): F[Response[T]] =
    adjustExceptions(request) {
      if (request.isWebSocket) sendWebSocket(request) else sendRegular(request)
    }

  protected def createBodyHandler: HttpResponse.BodyHandler[BH]
  protected def createSimpleQueue[T]: F[SimpleQueue[F, T]]
  protected def createSequencer: F[Sequencer[F]]
  protected def bodyHandlerBodyToBody(p: BH): B
  protected def cancelLowLevelBody(p: BH): Unit =
    () // default implementation given for binary compatibility, should be overridden
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
    *
    * The default implementation is provided for binary compatibility and should be overridden for specific effects, as
    * it might not work for interrupted effects.
    */
  protected def ensureOnAbnormal[T](effect: F[T])(finalizer: => F[Unit]): F[T] = effect.handleError {
    case e: Throwable =>
      finalizer.handleError { case e2: Throwable => monad.unit(()) }.flatMap { _ => monad.error(e) }
  }

  private def sendRegular[T, R >: PE](request: Request[T, R]): F[Response[T]] = {
    monad.flatMap(convertRequest(request)) { convertedRequest =>
      val jRequest = customizeRequest(convertedRequest)

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
              .map(bodyHandlerBodyToBody)
              .getOrElse(emptyBody())

            readResponse(jResponse, Left(body), request)
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

  private def sendWebSocket[T, R >: PE](request: Request[T, R]): F[Response[T]] = {
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

  private def sendWebSocket[T, R >: PE](
      request: Request[T, R],
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
              monad,
              sequencer
            )
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
        Canceler { () =>
          val _ = cf.cancel(true)
        }
      })
    } {
      val llws = lowLevelWS.get()
      if (llws != null) monad.eval(llws.abort()) else monad.unit(())
    }
  }

  private def filterIllegalWsHeaders[T, R](request: Request[T, R]): RequestT[Identity, T, R] = {
    request.copy(headers = request.headers.filter(h => !wsIllegalHeaders.contains(h.name.toLowerCase)))
  }

  private def adjustExceptions[T](request: Request[_, _])(t: => F[T]): F[T] =
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
