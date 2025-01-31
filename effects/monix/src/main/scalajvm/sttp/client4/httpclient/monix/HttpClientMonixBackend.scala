package sttp.client4.httpclient.monix

import cats.effect.ExitCase
import cats.effect.Resource
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import org.reactivestreams.FlowAdapters
import sttp.capabilities.StreamMaxLengthExceededException
import sttp.capabilities.monix.MonixStreams
import sttp.client4.BackendOptions
import sttp.client4.WebSocketStreamBackend
import sttp.client4.compression.CompressionHandlers
import sttp.client4.compression.Compressor
import sttp.client4.httpclient.HttpClientAsyncBackend
import sttp.client4.httpclient.HttpClientBackend
import sttp.client4.impl.monix.MonixSimpleQueue
import sttp.client4.impl.monix.TaskMonadAsyncError
import sttp.client4.internal._
import sttp.client4.internal.httpclient.BodyFromHttpClient
import sttp.client4.internal.httpclient.BodyToHttpClient
import sttp.client4.internal.httpclient.Sequencer
import sttp.client4.internal.httpclient.cancelPublisher
import sttp.client4.internal.ws.SimpleQueue
import sttp.client4.testing.WebSocketStreamBackendStub
import sttp.client4.wrappers
import sttp.monad.MonadError

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.nio.ByteBuffer
import java.{util => ju}
import java.util.concurrent.Flow.Publisher
import scala.collection.JavaConverters._

class HttpClientMonixBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    compressionHandlers: CompressionHandlers[MonixStreams, MonixStreams.BinaryStream]
)(implicit s: Scheduler)
    extends HttpClientAsyncBackend[Task, MonixStreams, Publisher[ju.List[ByteBuffer]], MonixStreams.BinaryStream](
      client,
      TaskMonadAsyncError,
      closeClient,
      customizeRequest,
      compressionHandlers
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
      override def compressors: List[Compressor[R]] = compressionHandlers.compressors
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

  override protected def bodyToLimitedBody(b: Observable[Array[Byte]], limit: Long): Observable[Array[Byte]] = {
    b
      .scan((0L, Option.empty[Array[Byte]])) { case ((acc, _), chunk) =>
        val newAcc = acc + chunk.length
        if (newAcc > limit) (newAcc, None) // Signal overflow
        else (newAcc, Some(chunk)) // Allow the chunk
      }
      .flatMap {
        case (_, Some(chunk)) => Observable.now(chunk) // Pass through chunks
        case (_, None)        => Observable.raiseError(new StreamMaxLengthExceededException(limit))
      }
  }

  override protected def addOnEndCallbackToBody(
      b: Observable[Array[Byte]],
      callback: () => Unit
  ): Observable[Array[Byte]] = b.doOnComplete(Task(callback()))
}

object HttpClientMonixBackend {
  val DefaultCompressionHandlers: CompressionHandlers[MonixStreams, MonixStreams.BinaryStream] =
    CompressionHandlers(
      Compressor.default[MonixStreams],
      List(GZipMonixDecompressor, DeflateMonixDecompressor)
    )

  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      compressionHandlers: CompressionHandlers[MonixStreams, MonixStreams.BinaryStream]
  )(implicit
      s: Scheduler
  ): WebSocketStreamBackend[Task, MonixStreams] =
    wrappers.FollowRedirectsBackend(
      new HttpClientMonixBackend(client, closeClient, customizeRequest, compressionHandlers)(s)
    )

  def apply(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[MonixStreams, MonixStreams.BinaryStream] = DefaultCompressionHandlers
  )(implicit
      s: Scheduler = Scheduler.global
  ): Task[WebSocketStreamBackend[Task, MonixStreams]] =
    Task.eval(
      HttpClientMonixBackend(
        HttpClientBackend.defaultClient(options, Some(s)),
        closeClient = false, // we don't want to close Monix's scheduler
        customizeRequest,
        compressionHandlers
      )(s)
    )

  def resource(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[MonixStreams, MonixStreams.BinaryStream] = DefaultCompressionHandlers
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, WebSocketStreamBackend[Task, MonixStreams]] =
    Resource.make(apply(options, customizeRequest, compressionHandlers))(_.close())

  def resourceUsingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[MonixStreams, MonixStreams.BinaryStream] = DefaultCompressionHandlers
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, WebSocketStreamBackend[Task, MonixStreams]] =
    Resource.make(
      Task.eval(HttpClientMonixBackend(client, closeClient = true, customizeRequest, compressionHandlers)(s))
    )(_.close())

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[MonixStreams, MonixStreams.BinaryStream] = DefaultCompressionHandlers
  )(implicit s: Scheduler = Scheduler.global): WebSocketStreamBackend[Task, MonixStreams] =
    HttpClientMonixBackend(client, closeClient = false, customizeRequest, compressionHandlers)(s)

  /** Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports `Observable[ByteBuffer]`
    * streaming.
    *
    * See [[sttp.client4.testing.BackendStub]] for details on how to configure stub responses.
    */
  def stub: WebSocketStreamBackendStub[Task, MonixStreams] = WebSocketStreamBackendStub(TaskMonadAsyncError)
}
