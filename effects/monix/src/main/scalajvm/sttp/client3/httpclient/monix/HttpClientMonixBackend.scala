package sttp.client3.httpclient.monix

import cats.effect.ExitCase
import cats.effect.Resource
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import monix.reactive.compression._
import org.reactivestreams.FlowAdapters
import sttp.capabilities.WebSockets
import sttp.capabilities.monix.MonixStreams
import sttp.client3.internal.httpclient.cancelPublisher
import sttp.client3.HttpClientBackend.EncodingHandler
import sttp.client3.httpclient.monix.HttpClientMonixBackend.MonixEncodingHandler
import sttp.client3.impl.monix.{MonixSimpleQueue, TaskMonadAsyncError}
import sttp.client3.internal._
import sttp.client3.internal.httpclient.{BodyFromHttpClient, BodyToHttpClient, Sequencer}
import sttp.client3.internal.ws.SimpleQueue
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{FollowRedirectsBackend, HttpClientAsyncBackend, HttpClientBackend, SttpBackend, SttpBackendOptions}
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
    extends HttpClientAsyncBackend[Task, MonixStreams, MonixStreams with WebSockets, Publisher[
      ju.List[ByteBuffer]
    ], MonixStreams.BinaryStream](
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
      override def streamToPublisher(stream: Observable[Array[Byte]]): Task[HttpRequest.BodyPublisher] =
        monad.eval(
          BodyPublishers.fromPublisher(FlowAdapters.toFlowPublisher(stream.map(ByteBuffer.wrap).toReactivePublisher))
        )
    }

  override protected val bodyFromHttpClient: BodyFromHttpClient[Task, MonixStreams, MonixStreams.BinaryStream] =
    new MonixBodyFromHttpClient {
      override implicit def scheduler: Scheduler = s
      override implicit def monad: MonadError[Task] = responseMonad
    }

  override protected def createSimpleQueue[T]: Task[SimpleQueue[Task, T]] =
    Task.eval(new MonixSimpleQueue[T](None))

  override protected def createSequencer: Task[Sequencer[Task]] = MonixSequencer.create

  override protected def createBodyHandler: HttpResponse.BodyHandler[Publisher[ju.List[ByteBuffer]]] =
    BodyHandlers.ofPublisher()

  override protected def lowLevelBodyToBody(p: Publisher[ju.List[ByteBuffer]]): Observable[Array[Byte]] =
    Observable
      .fromReactivePublisher(FlowAdapters.toPublisher(p))
      .flatMapIterable(_.asScala.toList)
      .map(_.safeRead())

  override protected def cancelLowLevelBody(p: Publisher[ju.List[ByteBuffer]]): Unit = cancelPublisher(p)

  override protected def ensureOnAbnormal[T](effect: Task[T])(finalizer: => Task[Unit]): Task[T] =
    effect.guaranteeCase { exit =>
      if (exit == ExitCase.Completed) Task.unit else finalizer.onErrorHandleWith(t => Task.eval(t.printStackTrace()))
    }

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
        HttpClientBackend.defaultClient(options, Some(s)),
        closeClient = false, // we don't want to close Monix's scheduler
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

  def resourceUsingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: MonixEncodingHandler = PartialFunction.empty
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, SttpBackend[Task, MonixStreams with WebSockets]] =
    Resource.make(
      Task.eval(HttpClientMonixBackend(client, closeClient = true, customizeRequest, customEncodingHandler)(s))
    )(_.close())

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: MonixEncodingHandler = PartialFunction.empty
  )(implicit s: Scheduler = Scheduler.global): SttpBackend[Task, MonixStreams with WebSockets] =
    HttpClientMonixBackend(client, closeClient = false, customizeRequest, customEncodingHandler)(s)

  /** Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports `Observable[ByteBuffer]`
    * streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[Task, MonixStreams with WebSockets] = SttpBackendStub(TaskMonadAsyncError)
}
