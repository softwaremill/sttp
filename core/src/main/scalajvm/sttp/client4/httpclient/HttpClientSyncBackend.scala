package sttp.client4.httpclient

import sttp.capabilities.WebSockets
import sttp.client4.httpclient.HttpClientBackend.EncodingHandler
import sttp.client4.httpclient.HttpClientSyncBackend.SyncEncodingHandler
import sttp.client4.internal.{emptyInputStream, NoStreams}
import sttp.client4.internal.SttpToJavaConverters.toJavaFunction
import sttp.client4.internal.httpclient.{
  AddToQueueListener,
  BodyFromHttpClient,
  BodyToHttpClient,
  DelegatingWebSocketListener,
  InputStreamBodyFromHttpClient,
  WebSocketImpl
}
import sttp.client4.internal.ws.{SimpleQueue, SyncQueue, WebSocketEvent}
import sttp.client4.monad.IdMonad
import sttp.client4.testing.WebSocketBackendStub
import sttp.client4.{wrappers, BackendOptions, GenericRequest, Identity, Response, WebSocketBackend}
import sttp.model.{HeaderNames, StatusCode}
import sttp.monad.MonadError
import sttp.monad.syntax.MonadErrorOps
import sttp.ws.{WebSocket, WebSocketFrame}

import java.io.{InputStream, UnsupportedEncodingException}
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, WebSocketHandshakeException}
import java.time.Duration
import java.util.concurrent.{ArrayBlockingQueue, CompletionException}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.{GZIPInputStream, InflaterInputStream}
import scala.concurrent.{blocking, Await, ExecutionContext, Future}

class HttpClientSyncBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: SyncEncodingHandler
) extends HttpClientBackend[Identity, Nothing, WebSockets, InputStream](
      client,
      closeClient,
      customEncodingHandler
    )
    with WebSocketBackend[Identity] {

  private implicit val ec: ExecutionContext = ExecutionContext.global
  override val streams: NoStreams = NoStreams

  override protected def sendRegular[T](request: GenericRequest[T, R]): Identity[Response[T]] = {
    val jRequest = customizeRequest(convertRequest(request))
    val response = client.send(jRequest, BodyHandlers.ofInputStream())
    readResponse(response, Left(response.body()), request)
  }

  override protected def sendWebSocket[T](request: GenericRequest[T, R]): Identity[Response[T]] = {
    val queue = createSimpleQueue[WebSocketEvent]
    sendWebSocket(request, queue).handleError {
      case e: CompletionException if e.getCause.isInstanceOf[WebSocketHandshakeException] =>
        readResponse(
          e.getCause.asInstanceOf[WebSocketHandshakeException].getResponse,
          Left(emptyInputStream()),
          request
        )
    }(monad)
  }

  private def sendWebSocket[T](
      request: GenericRequest[T, R],
      queue: SimpleQueue[Identity, WebSocketEvent]
  ): Identity[Response[T]] = {
    val isOpen: AtomicBoolean = new AtomicBoolean(false)
    val responseCell = new ArrayBlockingQueue[Either[Throwable, Future[Response[T]]]](5)

    def fillCellError(t: Throwable): Unit = responseCell.add(Left(t))

    def fillCell(wr: Future[Response[T]]): Unit = responseCell.add(Right(wr))

    val listener = new DelegatingWebSocketListener(
      new AddToQueueListener(queue, isOpen),
      ws => {
        val webSocket = WebSocketImpl.sync[Identity](ws, queue, isOpen, monad)
        val baseResponse = Response((), StatusCode.SwitchingProtocols, "", Nil, Nil, request.onlyMetadata)
        val body = Future(blocking(bodyFromHttpClient(Right(webSocket), request.response, baseResponse)))
        val wsResponse = body.map(b => baseResponse.copy(body = b))
        fillCell(wsResponse)
      },
      fillCellError
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
    wsBuilder
      .buildAsync(request.uri.toJavaUri, listener)
      .get()
    val response = responseCell.take().fold(throw _, identity)
    Await.result(response, scala.concurrent.duration.Duration.Inf)
  }
  override protected def createSimpleQueue[T]: Identity[SimpleQueue[Identity, T]] = new SyncQueue[T](None)

  override def monad: MonadError[Identity] = IdMonad

  override protected val bodyToHttpClient: BodyToHttpClient[Identity, Nothing] =
    new BodyToHttpClient[Identity, Nothing] {
      override val streams: NoStreams = NoStreams
      override implicit val monad: MonadError[Identity] = IdMonad
      override def streamToPublisher(stream: Nothing): Identity[BodyPublisher] = stream // nothing is everything
    }

  override protected val bodyFromHttpClient: BodyFromHttpClient[Identity, Nothing, InputStream] =
    new InputStreamBodyFromHttpClient[Identity, Nothing] {
      override def inputStreamToStream(is: InputStream): Identity[(streams.BinaryStream, () => Identity[Unit])] =
        monad.error(new IllegalStateException("Streaming is not supported"))
      override val streams: NoStreams = NoStreams
      override implicit def monad: MonadError[Identity] = IdMonad
      override def compileWebSocketPipe(
          ws: WebSocket[Identity],
          pipe: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
      ): Identity[Unit] = pipe
    }

  override protected def standardEncoding: (InputStream, String) => InputStream = {
    case (body, "gzip")    => new GZIPInputStream(body)
    case (body, "deflate") => new InflaterInputStream(body)
    case (_, ce)           => throw new UnsupportedEncodingException(s"Unsupported encoding: $ce")
  }
}

object HttpClientSyncBackend {
  type SyncEncodingHandler = EncodingHandler[InputStream]

  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      customEncodingHandler: SyncEncodingHandler
  ): WebSocketBackend[Identity] =
    wrappers.FollowRedirectsBackend(
      new HttpClientSyncBackend(client, closeClient, customizeRequest, customEncodingHandler)
    )

  def apply(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: SyncEncodingHandler = PartialFunction.empty
  ): WebSocketBackend[Identity] =
    HttpClientSyncBackend(
      HttpClientBackend.defaultClient(options, None),
      closeClient = true,
      customizeRequest,
      customEncodingHandler
    )

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: SyncEncodingHandler = PartialFunction.empty
  ): WebSocketBackend[Identity] =
    HttpClientSyncBackend(
      client,
      closeClient = false,
      customizeRequest,
      customEncodingHandler
    )

  /** Create a stub backend for testing. See [[SyncBackendStub]] for details on how to configure stub responses. */
  def stub: WebSocketBackendStub[Identity] = WebSocketBackendStub.synchronous
}
