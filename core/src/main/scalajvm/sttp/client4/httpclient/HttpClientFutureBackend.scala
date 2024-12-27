package sttp.client4.httpclient

import sttp.client4.httpclient.HttpClientBackend.EncodingHandler
import sttp.client4.httpclient.HttpClientFutureBackend.InputStreamEncodingHandler
import sttp.client4.internal.httpclient._
import sttp.client4.internal.ws.{FutureSimpleQueue, SimpleQueue}
import sttp.client4.internal.{emptyInputStream, NoStreams}
import sttp.client4.testing.WebSocketBackendStub
import sttp.client4.{wrappers, BackendOptions, WebSocketBackend}
import sttp.monad.{FutureMonad, MonadError}
import sttp.ws.{WebSocket, WebSocketFrame}

import java.io.{InputStream, UnsupportedEncodingException}
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.util.concurrent.Executor
import java.util.zip.{GZIPInputStream, InflaterInputStream}
import scala.concurrent.{ExecutionContext, Future}

class HttpClientFutureBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: InputStreamEncodingHandler
)(implicit ec: ExecutionContext)
    extends HttpClientAsyncBackend[Future, Nothing, InputStream, InputStream](
      client,
      new FutureMonad,
      closeClient,
      customizeRequest,
      customEncodingHandler
    ) {

  override val streams: NoStreams = NoStreams

  override protected val bodyToHttpClient: BodyToHttpClient[Future, Nothing, R] =
    new BodyToHttpClient[Future, Nothing, R] {
      override val streams: NoStreams = NoStreams
      override implicit val monad: MonadError[Future] = new FutureMonad
      override def streamToPublisher(stream: Nothing): Future[BodyPublisher] = stream // nothing is everything
    }

  override protected val bodyFromHttpClient: BodyFromHttpClient[Future, Nothing, InputStream] =
    new InputStreamBodyFromHttpClient[Future, Nothing] {
      override def inputStreamToStream(is: InputStream): Future[(streams.BinaryStream, () => Future[Unit])] =
        monad.error(new IllegalStateException("Streaming is not supported"))
      override val streams: NoStreams = NoStreams
      override implicit def monad: MonadError[Future] = new FutureMonad()
      override def compileWebSocketPipe(
          ws: WebSocket[Future],
          pipe: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
      ): Future[Unit] = pipe
    }

  override protected def createSimpleQueue[T]: Future[SimpleQueue[Future, T]] =
    Future.successful(new FutureSimpleQueue[T](None))

  override protected def createSequencer: Future[Sequencer[Future]] = Future.successful(new FutureSequencer)

  override protected def standardEncoding: (InputStream, String) => InputStream = {
    case (body, "gzip")    => new GZIPInputStream(body)
    case (body, "deflate") => new InflaterInputStream(body)
    case (_, ce)           => throw new UnsupportedEncodingException(s"Unsupported encoding: $ce")
  }

  override protected def createBodyHandler: HttpResponse.BodyHandler[InputStream] = BodyHandlers.ofInputStream()

  override protected def bodyHandlerBodyToBody(p: InputStream): InputStream = p

  override protected def emptyBody(): InputStream = emptyInputStream()
}

object HttpClientFutureBackend {
  type InputStreamEncodingHandler = EncodingHandler[InputStream]

  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      customEncodingHandler: InputStreamEncodingHandler
  )(implicit ec: ExecutionContext): WebSocketBackend[Future] =
    wrappers.FollowRedirectsBackend(
      new HttpClientFutureBackend(client, closeClient, customizeRequest, customEncodingHandler)
    )

  def apply(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: InputStreamEncodingHandler = PartialFunction.empty
  )(implicit ec: ExecutionContext = ExecutionContext.global): WebSocketBackend[Future] = {
    val executor = Some(ec).collect { case executor: Executor => executor }
    HttpClientFutureBackend(
      HttpClientBackend.defaultClient(options, executor),
      closeClient = executor.isEmpty,
      customizeRequest,
      customEncodingHandler
    )
  }

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: InputStreamEncodingHandler = PartialFunction.empty
  )(implicit ec: ExecutionContext = ExecutionContext.global): WebSocketBackend[Future] =
    HttpClientFutureBackend(
      client,
      closeClient = false,
      customizeRequest,
      customEncodingHandler
    )

  /** Create a stub backend for testing, which uses [[Future]] to represent side effects, and doesn't support streaming.
    *
    * See [[WebSocketBackendStub]] for details on how to configure stub responses.
    */
  def stub(implicit ec: ExecutionContext = ExecutionContext.global): WebSocketBackendStub[Future] =
    WebSocketBackendStub.asynchronousFuture
}
