package sttp.client.asynchttpclient.monix

import java.io.File
import java.nio.ByteBuffer

import cats.effect.Resource
import io.netty.buffer.{ByteBuf, Unpooled}
import monix.eval.Task
import monix.execution.Scheduler
import monix.execution.cancelables.BooleanCancelable
import monix.nio.file._
import monix.reactive.Observable
import org.asynchttpclient._
import org.reactivestreams.Publisher
import sttp.client.asynchttpclient.{AsyncHttpClientBackend, BodyFromAHC, BodyToAHC}
import sttp.client.impl.monix.{MonixAsyncQueue, MonixStreams, TaskMonadAsyncError}
import sttp.client.internal._
import sttp.client.monad.MonadAsyncError
import sttp.client.testing.SttpBackendStub
import sttp.client.ws.WebSocket
import sttp.client.ws.internal.AsyncQueue
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions, WebSockets}
import sttp.model.ws.WebSocketFrame

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

      override def publisherToStream(p: Publisher[ByteBuffer]): Observable[ByteBuffer] =
        Observable.fromReactivePublisher(p)

      override def publisherToBytes(p: Publisher[ByteBuffer]): Task[Array[Byte]] = {
        val bytes = Observable
          .fromReactivePublisher(p)
          .foldLeftL(ByteBuffer.allocate(0))(concatByteBuffers)

        bytes.map(_.array())
      }

      override def publisherToFile(p: Publisher[ByteBuffer], f: File): Task[Unit] = {
        Observable
          .fromReactivePublisher(p)
          .map(_.array())
          .consumeWith(writeAsync(f.toPath))
          .map(_ => ())
      }

      override def compileWebSocketPipe(
          ws: WebSocket[Task],
          pipe: Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame]
      ): Task[Unit] = {
        Task(BooleanCancelable()).flatMap { wsClosed =>
          Observable
            .repeatEvalF(ws.receive)
            .flatMap {
              case Left(WebSocketFrame.Close(_, _))    => Observable.fromTask(Task(wsClosed.cancel()))
              case Right(WebSocketFrame.Ping(payload)) => Observable.fromTask(ws.send(WebSocketFrame.Pong(payload)))
              case Right(WebSocketFrame.Pong(_))       => Observable.empty
              case Right(in: WebSocketFrame.Data[_])   => pipe(Observable(in)).mapEval(ws.send(_))
            }
            .takeWhileNotCanceled(wsClosed)
            .completedL
            .guarantee(ws.close)
        }
      }
    }

  override protected val bodyToAHC: BodyToAHC[Task, MonixStreams] = new BodyToAHC[Task, MonixStreams] {
    override val streams: MonixStreams = MonixStreams

    override protected def streamToPublisher(s: Observable[ByteBuffer]): Publisher[ByteBuf] =
      s.map(Unpooled.wrappedBuffer).toReactivePublisher
  }

  override protected def createAsyncQueue[T]: AsyncQueue[Task, T] = new MonixAsyncQueue[T](webSocketBufferCapacity)
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

  /**
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
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

  /**
    * Makes sure the backend is closed after usage.
    */
  def resource(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, SttpBackend[Task, MonixStreams with WebSockets]] =
    Resource.make(apply(options, customizeRequest, webSocketBufferCapacity))(_.close())

  /**
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
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

  /**
    * Makes sure the backend is closed after usage.
    */
  def resourceUsingConfig(
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, SttpBackend[Task, MonixStreams with WebSockets]] =
    Resource.make(usingConfig(cfg, customizeRequest, webSocketBufferCapacity))(_.close())

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
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

  /**
    * Makes sure the backend is closed after usage.
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
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

  /**
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
  def usingClient(
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  )(implicit s: Scheduler = Scheduler.global): SttpBackend[Task, MonixStreams with WebSockets] =
    AsyncHttpClientMonixBackend(client, closeClient = false, customizeRequest, webSocketBufferCapacity)

  /**
    * Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports `Observable[ByteBuffer]`
    * streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[Task, MonixStreams with WebSockets] = SttpBackendStub(TaskMonadAsyncError)
}
