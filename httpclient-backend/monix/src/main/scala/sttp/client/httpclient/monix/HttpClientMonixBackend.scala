package sttp.client.httpclient.monix

import java.io.{InputStream, UnsupportedEncodingException}
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.{HttpClient, HttpRequest}
import java.nio.ByteBuffer
import java.util
import java.util.zip.{GZIPInputStream, InflaterInputStream}

import cats.effect.Resource
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import org.reactivestreams.{FlowAdapters, Publisher}
import sttp.capabilities.WebSockets
import sttp.capabilities.monix.MonixStreams
import sttp.client.httpclient.HttpClientBackend.EncodingHandler
import sttp.client.httpclient.monix.HttpClientMonixBackend.MonixEncodingHandler
import sttp.client.httpclient.{
  BodyFromHttpClient,
  BodyToHttpClient,
  HttpClientAsyncBackend,
  HttpClientBackend,
  InputStreamBodyFromHttpClient,
  InputStreamSubscriber
}
import sttp.client.impl.monix.{MonixSimpleQueue, MonixWebSockets, TaskMonadAsyncError}
import sttp.client.internal._
import sttp.client.internal.ws.SimpleQueue
import sttp.client.testing.SttpBackendStub
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import sttp.monad.MonadError
import sttp.ws.{WebSocket, WebSocketFrame}

class HttpClientMonixBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: MonixEncodingHandler
)(implicit s: Scheduler)
    extends HttpClientAsyncBackend[Task, MonixStreams, MonixStreams with WebSockets, InputStream](
      client,
      TaskMonadAsyncError,
      closeClient,
      customizeRequest,
      customEncodingHandler
    ) {

  override val streams: MonixStreams = MonixStreams

  override protected val bodyToHttpClient: BodyToHttpClient[Task, MonixStreams] =
    new BodyToHttpClient[Task, MonixStreams] {
      override val streams: MonixStreams = MonixStreams
      override implicit def monad: MonadError[Task] = responseMonad
      override def streamToPublisher(stream: Observable[ByteBuffer]): Task[HttpRequest.BodyPublisher] =
        monad.eval(BodyPublishers.fromPublisher(FlowAdapters.toFlowPublisher(stream.toReactivePublisher)))
    }

  override protected val bodyFromHttpClient: BodyFromHttpClient[Task, MonixStreams, InputStream] =
    new InputStreamBodyFromHttpClient[Task, MonixStreams] {
      override def inputStreamToStream(is: InputStream): Task[(streams.BinaryStream, () => Task[Unit])] =
        monad.error(new IllegalStateException("Streaming is not supported"))
      override val streams: MonixStreams = MonixStreams
      override implicit def monad: MonadError[Task] = TaskMonadAsyncError
      override def compileWebSocketPipe(
          ws: WebSocket[Task],
          pipe: Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame]
      ): Task[Unit] = MonixWebSockets.compilePipe(ws, pipe)
    }

  override protected def createSimpleQueue[T]: Task[SimpleQueue[Task, T]] =
    Task.eval(new MonixSimpleQueue[T](None))

  override protected def publisherToBody(p: Publisher[util.List[ByteBuffer]]): InputStream = {
    val subscriber = new InputStreamSubscriber
    p.subscribe(subscriber)
    subscriber.inputStream
  }

  override protected def emptyBody(): InputStream = emptyInputStream()

  override protected def standardEncoding: (InputStream, String) => InputStream = {
    case (body, "gzip")    => new GZIPInputStream(body)
    case (body, "deflate") => new InflaterInputStream(body)
    case (_, ce)           => throw new UnsupportedEncodingException(s"Unsupported encoding: $ce")
  }
}

object HttpClientMonixBackend {
  type MonixEncodingHandler = EncodingHandler[InputStream]

  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      customEncodingHandler: MonixEncodingHandler
  )(implicit
      s: Scheduler
  ): SttpBackend[Task, MonixStreams with WebSockets] =
    new FollowRedirectsBackend(
      new HttpClientMonixBackend(client, closeClient, customizeRequest, customEncodingHandler)(s)
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: MonixEncodingHandler = PartialFunction.empty
  )(implicit
      s: Scheduler = Scheduler.global
  ): Task[SttpBackend[Task, MonixStreams with WebSockets]] =
    Task.eval(
      HttpClientMonixBackend(
        HttpClientBackend.defaultClient(options),
        closeClient = true,
        customizeRequest,
        customEncodingHandler
      )(s)
    )

  def resource(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: MonixEncodingHandler = PartialFunction.empty
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, SttpBackend[Task, MonixStreams with WebSockets]] =
    Resource.make(apply(options, customizeRequest, customEncodingHandler))(_.close())

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: MonixEncodingHandler = PartialFunction.empty
  )(implicit s: Scheduler = Scheduler.global): SttpBackend[Task, MonixStreams with WebSockets] =
    HttpClientMonixBackend(client, closeClient = false, customizeRequest, customEncodingHandler)(s)

  /**
    * Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports `Observable[ByteBuffer]`
    * streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[Task, MonixStreams with WebSockets] = SttpBackendStub(TaskMonadAsyncError)
}
