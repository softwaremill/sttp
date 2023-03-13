package sttp.client4.asynchttpclient.monix

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
import sttp.client4.asynchttpclient.{AsyncHttpClientBackend, BodyFromAHC, BodyToAHC}
import sttp.client4.impl.monix.{MonixSimpleQueue, MonixWebSockets, TaskMonadAsyncError}
import sttp.client4.internal._
import sttp.client4.internal.ws.SimpleQueue
import sttp.client4.testing.WebSocketStreamBackendStub
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.client4.{BackendOptions, WebSocketStreamBackend, wrappers}
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
    )
    with WebSocketStreamBackend[Task, MonixStreams] {

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
  ): WebSocketStreamBackend[Task, MonixStreams] =
    wrappers.FollowRedirectsBackend(
      new AsyncHttpClientMonixBackend(asyncHttpClient, closeClient, customizeRequest, webSocketBufferCapacity)
    )

  /** @param s The scheduler used for streaming request bodies. Defaults to the global scheduler. */
  def apply(
             options: BackendOptions = BackendOptions.Default,
             customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
             webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Task[WebSocketStreamBackend[Task, MonixStreams]] =
    Task.eval(
      AsyncHttpClientMonixBackend(
        AsyncHttpClientBackend.defaultClient(options),
        closeClient = true,
        customizeRequest,
        webSocketBufferCapacity
      )
    )

  /** Makes sure the backend is closed after usage.
    * @param s
    *   The scheduler used for streaming request bodies. Defaults to the global scheduler.
    */
  def resource(
                options: BackendOptions = BackendOptions.Default,
                customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
                webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, WebSocketStreamBackend[Task, MonixStreams]] =
    Resource.make(apply(options, customizeRequest, webSocketBufferCapacity))(_.close())

  /** @param s The scheduler used for streaming request bodies. Defaults to the global scheduler. */
  def usingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Task[WebSocketStreamBackend[Task, MonixStreams]] =
    Task.eval(
      AsyncHttpClientMonixBackend(
        new DefaultAsyncHttpClient(cfg),
        closeClient = true,
        customizeRequest,
        webSocketBufferCapacity
      )
    )

  /** Makes sure the backend is closed after usage.
    * @param s
    *   The scheduler used for streaming request bodies. Defaults to the global scheduler.
    */
  def resourceUsingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, WebSocketStreamBackend[Task, MonixStreams]] =
    Resource.make(usingConfig(cfg, customizeRequest, webSocketBufferCapacity))(_.close())

  /** @param updateConfig
    *   A function which updates the default configuration (created basing on `options`).
    * @param s
    *   The scheduler used for streaming request bodies. Defaults to the global scheduler.
    */
  def usingConfigBuilder(
                          updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
                          options: BackendOptions = BackendOptions.Default,
                          customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
                          webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Task[WebSocketStreamBackend[Task, MonixStreams]] =
    Task.eval(
      AsyncHttpClientMonixBackend(
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true,
        customizeRequest,
        webSocketBufferCapacity
      )
    )

  /** Makes sure the backend is closed after usage.
    * @param updateConfig
    *   A function which updates the default configuration (created basing on `options`).
    * @param s
    *   The scheduler used for streaming request bodies. Defaults to the global scheduler.
    */
  def resourceUsingConfigBuilder(
                                  updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
                                  options: BackendOptions = BackendOptions.Default,
                                  customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
                                  webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, WebSocketStreamBackend[Task, MonixStreams]] =
    Resource.make(usingConfigBuilder(updateConfig, options, customizeRequest, webSocketBufferCapacity))(_.close())

  /** @param s The scheduler used for streaming request bodies. Defaults to the global scheduler. */
  def usingClient(
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit s: Scheduler = Scheduler.global): WebSocketStreamBackend[Task, MonixStreams] =
    AsyncHttpClientMonixBackend(client, closeClient = false, customizeRequest, webSocketBufferCapacity)

  /** Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports `Observable[ByteBuffer]`
    * streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: WebSocketStreamBackendStub[Task, MonixStreams] = WebSocketStreamBackendStub(TaskMonadAsyncError)
}
