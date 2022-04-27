package sttp.client3

import sttp.capabilities.WebSockets
import sttp.client3.HttpClientBackend.EncodingHandler
import sttp.client3.HttpClientFutureBackend.InputStreamEncodingHandler
import sttp.client3.internal.httpclient.{
  BodyFromHttpClient,
  BodyToHttpClient,
  FutureSequencer,
  InputStreamBodyFromHttpClient,
  InputStreamSubscriber,
  Sequencer
}
import sttp.client3.internal.ws.{FutureSimpleQueue, SimpleQueue}
import sttp.client3.internal.{NoStreams, emptyInputStream}
import sttp.client3.testing.SttpBackendStub
import sttp.monad.{FutureMonad, MonadError}
import sttp.ws.{WebSocket, WebSocketFrame}

import java.io.{InputStream, UnsupportedEncodingException}
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.{HttpClient, HttpRequest}
import java.nio.ByteBuffer
import java.util
import java.util.concurrent.Flow.Publisher
import java.util.zip.{GZIPInputStream, InflaterInputStream}
import scala.concurrent.{ExecutionContext, Future}

class HttpClientFutureBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: InputStreamEncodingHandler
)(implicit ec: ExecutionContext)
    extends HttpClientAsyncBackend[Future, Nothing, WebSockets, InputStream](
      client,
      new FutureMonad,
      closeClient,
      customizeRequest,
      customEncodingHandler
    ) {

  override val streams: NoStreams = NoStreams

  override protected val bodyToHttpClient: BodyToHttpClient[Future, Nothing] = new BodyToHttpClient[Future, Nothing] {
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

  override protected def emptyBody(): InputStream = emptyInputStream()

  override protected def publisherToBody(p: Publisher[util.List[ByteBuffer]]): InputStream = {
    val subscriber = new InputStreamSubscriber
    p.subscribe(subscriber)
    subscriber.inputStream
  }
}

object HttpClientFutureBackend {
  type InputStreamEncodingHandler = EncodingHandler[InputStream]

  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      customEncodingHandler: InputStreamEncodingHandler
  )(implicit ec: ExecutionContext): SttpBackend[Future, WebSockets] =
    new FollowRedirectsBackend(
      new HttpClientFutureBackend(
        client,
        closeClient,
        customizeRequest,
        customEncodingHandler
      )
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: InputStreamEncodingHandler = PartialFunction.empty
  )(implicit ec: ExecutionContext = ExecutionContext.global): SttpBackend[Future, WebSockets] =
    HttpClientFutureBackend(
      HttpClientBackend.defaultClient(options),
      closeClient = true,
      customizeRequest,
      customEncodingHandler
    )

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: InputStreamEncodingHandler = PartialFunction.empty
  )(implicit ec: ExecutionContext = ExecutionContext.global): SttpBackend[Future, WebSockets] =
    HttpClientFutureBackend(
      client,
      closeClient = false,
      customizeRequest,
      customEncodingHandler
    )

  /** Create a stub backend for testing, which uses the [[Future]] response wrapper, and doesn't support streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub(implicit ec: ExecutionContext = ExecutionContext.global): SttpBackendStub[Future, WebSockets] =
    SttpBackendStub(new FutureMonad())
}
