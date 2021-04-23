package sttp.client3.asynchttpclient.monix

import cats.effect.Resource
import io.netty.buffer.{ByteBuf, Unpooled}
import monix.eval.Task
import monix.execution.Scheduler
import monix.nio.file._
import monix.reactive.Observable
import org.asynchttpclient._
import org.reactivestreams.Publisher
import sttp.capabilities.WebSockets
import sttp.capabilities.monix.MonixStreams
import sttp.client3.asynchttpclient.{AsyncHttpClientBackend, BodyFromAHC, BodyToAHC}
import sttp.client3.impl.monix.{MonixSimpleQueue, MonixWebSockets, TaskMonadAsyncError}
import sttp.client3.internal._
import sttp.client3.internal.ws.SimpleQueue
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import sttp.monad.MonadAsyncError
import sttp.ws.{WebSocket, WebSocketFrame}

import java.io.File
import java.nio.ByteBuffer
import scala.collection.immutable.Queue

class AsyncHttpClientMonixBackend private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder,
    webSocketBufferCapacity: Option[Int]
)(implicit
    scheduler: Scheduler
) extends AsyncHttpClientBackend[Task, MonixStreams, MonixStreams with WebSockets](
      asyncHttpClient,
      TaskMonadAsyncError,
      closeClient,
      customizeRequest
    ) {

  override val streams: MonixStreams = MonixStreams

  override protected val bodyFromAHC: BodyFromAHC[Task, MonixStreams] =
    new BodyFromAHC[Task, MonixStreams] {
      override val streams: MonixStreams = MonixStreams
      override implicit val monad: MonadAsyncError[Task] = TaskMonadAsyncError

      override def publisherToStream(p: Publisher[ByteBuffer]): Observable[Array[Byte]] =
        Observable.fromReactivePublisher(p).map(_.safeRead())

      override def publisherToBytes(p: Publisher[ByteBuffer]): Task[Array[Byte]] = {
        Observable
          .fromReactivePublisher(p)
          .foldLeftL(Queue.empty[Array[Byte]])(enqueueBytes)
          .map(concatBytes)
      }

      override def publisherToFile(p: Publisher[ByteBuffer], f: File): Task[Unit] = {
        Observable
          .fromReactivePublisher(p)
          .map(_.array())
          .consumeWith(writeAsync(f.toPath))
          .map(_ => ())
      }

      override def bytesToPublisher(b: Array[Byte]): Task[Publisher[ByteBuffer]] =
        Task.now(Observable.eval(ByteBuffer.wrap(b)).toReactivePublisher)

      override def fileToPublisher(f: File): Task[Publisher[ByteBuffer]] =
        Task.now(readAsync(f.toPath, IOBufferSize).map(ByteBuffer.wrap).toReactivePublisher)

      override def compileWebSocketPipe(
          ws: WebSocket[Task],
          pipe: Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame]
      ): Task[Unit] = MonixWebSockets.compilePipe(ws, pipe)
    }

  override protected val bodyToAHC: BodyToAHC[Task, MonixStreams] = new BodyToAHC[Task, MonixStreams] {
    override val streams: MonixStreams = MonixStreams

    override protected def streamToPublisher(s: Observable[Array[Byte]]): Publisher[ByteBuf] =
      s.map(Unpooled.wrappedBuffer).toReactivePublisher
  }

  override protected def createSimpleQueue[T]: Task[SimpleQueue[Task, T]] =
    Task.eval(new MonixSimpleQueue[T](webSocketBufferCapacity))
}

object AsyncHttpClientMonixBackend {
  private def apply(
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder,
      webSocketBufferCapacity: Option[Int]
  )(implicit
      scheduler: Scheduler
  ): SttpBackend[Task, MonixStreams with WebSockets] =
    new FollowRedirectsBackend(
      new AsyncHttpClientMonixBackend(asyncHttpClient, closeClient, customizeRequest, webSocketBufferCapacity)
    )

  /** @param s The scheduler used for streaming request bodies. Defaults to the global scheduler. */
  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Task[SttpBackend[Task, MonixStreams with WebSockets]] =
    Task.eval(
      AsyncHttpClientMonixBackend(
        AsyncHttpClientBackend.defaultClient(options),
        closeClient = true,
        customizeRequest,
        webSocketBufferCapacity
      )
    )

  /** Makes sure the backend is closed after usage.
    * @param s The scheduler used for streaming request bodies. Defaults to the global scheduler.
    */
  def resource(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, SttpBackend[Task, MonixStreams with WebSockets]] =
    Resource.make(apply(options, customizeRequest, webSocketBufferCapacity))(_.close())

  /** @param s The scheduler used for streaming request bodies. Defaults to the global scheduler. */
  def usingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Task[SttpBackend[Task, MonixStreams with WebSockets]] =
    Task.eval(
      AsyncHttpClientMonixBackend(
        new DefaultAsyncHttpClient(cfg),
        closeClient = true,
        customizeRequest,
        webSocketBufferCapacity
      )
    )

  /** Makes sure the backend is closed after usage.
    * @param s The scheduler used for streaming request bodies. Defaults to the global scheduler.
    */
  def resourceUsingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, SttpBackend[Task, MonixStreams with WebSockets]] =
    Resource.make(usingConfig(cfg, customizeRequest, webSocketBufferCapacity))(_.close())

  /** @param updateConfig A function which updates the default configuration (created basing on `options`).
    * @param s The scheduler used for streaming request bodies. Defaults to the global scheduler.
    */
  def usingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Task[SttpBackend[Task, MonixStreams with WebSockets]] =
    Task.eval(
      AsyncHttpClientMonixBackend(
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true,
        customizeRequest,
        webSocketBufferCapacity
      )
    )

  /** Makes sure the backend is closed after usage.
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    * @param s The scheduler used for streaming request bodies. Defaults to the global scheduler.
    */
  def resourceUsingConfigBuilder(
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, SttpBackend[Task, MonixStreams with WebSockets]] =
    Resource.make(usingConfigBuilder(updateConfig, options, customizeRequest, webSocketBufferCapacity))(_.close())

  /** @param s The scheduler used for streaming request bodies. Defaults to the global scheduler. */
  def usingClient(
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit s: Scheduler = Scheduler.global): SttpBackend[Task, MonixStreams with WebSockets] =
    AsyncHttpClientMonixBackend(client, closeClient = false, customizeRequest, webSocketBufferCapacity)

  /** Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports `Observable[ByteBuffer]`
    * streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[Task, MonixStreams with WebSockets] = SttpBackendStub(TaskMonadAsyncError)
}
