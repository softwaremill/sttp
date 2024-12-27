package sttp.client4.httpclient.monix

import cats.effect.Resource
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import monix.reactive.compression._
import org.reactivestreams.FlowAdapters
import sttp.capabilities.monix.MonixStreams
import sttp.client4.httpclient.{HttpClientAsyncBackend, HttpClientBackend}
import sttp.client4.httpclient.HttpClientBackend.EncodingHandler
import sttp.client4.httpclient.monix.HttpClientMonixBackend.MonixEncodingHandler
import sttp.client4.impl.monix.{MonixSimpleQueue, TaskMonadAsyncError}
import sttp.client4.internal._
import sttp.client4.internal.httpclient.{BodyFromHttpClient, BodyToHttpClient, Sequencer}
import sttp.client4.internal.ws.SimpleQueue
import sttp.client4.testing.WebSocketStreamBackendStub
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.client4.{wrappers, BackendOptions, WebSocketStreamBackend}
import sttp.monad.MonadError

import java.io.UnsupportedEncodingException
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse.BodyHandlers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.ByteBuffer
import java.util.concurrent.Flow.Publisher
import java.{util => ju}
import scala.collection.JavaConverters._

class HttpClientMonixBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: MonixEncodingHandler
)(implicit s: Scheduler)
    extends HttpClientAsyncBackend[Task, MonixStreams, Publisher[ju.List[ByteBuffer]], MonixStreams.BinaryStream](
      client,
      TaskMonadAsyncError,
      closeClient,
      customizeRequest,
      customEncodingHandler
    )
    with WebSocketStreamBackend[Task, MonixStreams] { self =>

  override val streams: MonixStreams = MonixStreams

  override protected val bodyToHttpClient: BodyToHttpClient[Task, MonixStreams, R] =
    new BodyToHttpClient[Task, MonixStreams, R] {
      override val streams: MonixStreams = MonixStreams
      override implicit def monad: MonadError[Task] = self.monad
      override def streamToPublisher(stream: Observable[Array[Byte]]): Task[HttpRequest.BodyPublisher] =
        monad.eval(
          BodyPublishers.fromPublisher(FlowAdapters.toFlowPublisher(stream.map(ByteBuffer.wrap).toReactivePublisher))
        )
    }

  override protected val bodyFromHttpClient: BodyFromHttpClient[Task, MonixStreams, MonixStreams.BinaryStream] =
    new MonixBodyFromHttpClient {
      override implicit def scheduler: Scheduler = s
      override implicit def monad: MonadError[Task] = self.monad
    }

  override protected def createSimpleQueue[T]: Task[SimpleQueue[Task, T]] =
    Task.eval(new MonixSimpleQueue[T](None))

  override protected def createSequencer: Task[Sequencer[Task]] = MonixSequencer.create

  override protected def createBodyHandler: HttpResponse.BodyHandler[Publisher[ju.List[ByteBuffer]]] =
    BodyHandlers.ofPublisher()

  override protected def bodyHandlerBodyToBody(p: Publisher[ju.List[ByteBuffer]]): Observable[Array[Byte]] =
    Observable
      .fromReactivePublisher(FlowAdapters.toPublisher(p))
      .flatMapIterable(_.asScala.toList)
      .map(_.safeRead())

  override protected def emptyBody(): Observable[Array[Byte]] = Observable.empty

  override protected def standardEncoding: (Observable[Array[Byte]], String) => Observable[Array[Byte]] = {
    case (body, "gzip")    => body.transform(gunzip())
    case (body, "deflate") => body.transform(inflate())
    case (_, ce)           => throw new UnsupportedEncodingException(s"Unsupported encoding: $ce")
  }
}

object HttpClientMonixBackend {
  type MonixEncodingHandler = EncodingHandler[MonixStreams.BinaryStream]

  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      customEncodingHandler: MonixEncodingHandler
  )(implicit
      s: Scheduler
  ): WebSocketStreamBackend[Task, MonixStreams] =
    wrappers.FollowRedirectsBackend(
      new HttpClientMonixBackend(client, closeClient, customizeRequest, customEncodingHandler)(s)
    )

  def apply(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: MonixEncodingHandler = PartialFunction.empty
  )(implicit
      s: Scheduler = Scheduler.global
  ): Task[WebSocketStreamBackend[Task, MonixStreams]] =
    Task.eval(
      HttpClientMonixBackend(
        HttpClientBackend.defaultClient(options, Some(s)),
        closeClient = false, // we don't want to close Monix's scheduler
        customizeRequest,
        customEncodingHandler
      )(s)
    )

  def resource(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: MonixEncodingHandler = PartialFunction.empty
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, WebSocketStreamBackend[Task, MonixStreams]] =
    Resource.make(apply(options, customizeRequest, customEncodingHandler))(_.close())

  def resourceUsingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: MonixEncodingHandler = PartialFunction.empty
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, WebSocketStreamBackend[Task, MonixStreams]] =
    Resource.make(
      Task.eval(HttpClientMonixBackend(client, closeClient = true, customizeRequest, customEncodingHandler)(s))
    )(_.close())

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: MonixEncodingHandler = PartialFunction.empty
  )(implicit s: Scheduler = Scheduler.global): WebSocketStreamBackend[Task, MonixStreams] =
    HttpClientMonixBackend(client, closeClient = false, customizeRequest, customEncodingHandler)(s)

  /** Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports `Observable[ByteBuffer]`
    * streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: WebSocketStreamBackendStub[Task, MonixStreams] = WebSocketStreamBackendStub(TaskMonadAsyncError)
}
