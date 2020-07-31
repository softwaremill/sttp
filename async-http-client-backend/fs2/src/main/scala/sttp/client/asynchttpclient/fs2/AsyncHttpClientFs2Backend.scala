package sttp.client.asynchttpclient.fs2

import java.io.File
import java.nio.ByteBuffer

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import fs2.concurrent.InspectableQueue
import fs2.{Chunk, Pipe, Stream}
import fs2.interop.reactivestreams._
import io.netty.buffer.{ByteBuf, Unpooled}
import org.asynchttpclient.{Request => _, Response => _, _}
import org.reactivestreams.Publisher
import sttp.client.asynchttpclient.{AsyncHttpClientBackend, BodyFromAHC, BodyToAHC}
import sttp.client.impl.cats.CatsMonadAsyncError
import sttp.client.impl.fs2.{Fs2AsyncQueue, Fs2Streams, Fs2WebSockets}
import sttp.client.internal._
import sttp.client.monad.MonadAsyncError
import sttp.client.testing.SttpBackendStub
import sttp.client.ws.WebSocket
import sttp.client.ws.internal.AsyncQueue
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions, _}
import sttp.model.ws.WebSocketFrame

import scala.concurrent.ExecutionContext

class AsyncHttpClientFs2Backend[F[_]: ConcurrentEffect: ContextShift] private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder,
    webSocketBufferCapacity: Option[Int]
) extends AsyncHttpClientBackend[F, Fs2Streams[F], Fs2Streams[F] with WebSockets](
      asyncHttpClient,
      new CatsMonadAsyncError,
      closeClient,
      customizeRequest
    ) {

  override val streams: Fs2Streams[F] = Fs2Streams[F]

  override def send[T, R >: sttp.client.Effect[F] with Fs2Streams[F] with WebSockets](
      r: Request[T, R]
  ): F[Response[T]] = {
    super.send(r).guarantee(implicitly[ContextShift[F]].shift)
  }

  override protected val bodyFromAHC: BodyFromAHC[F, Fs2Streams[F]] =
    new BodyFromAHC[F, Fs2Streams[F]] {
      override val streams: Fs2Streams[F] = Fs2Streams[F]
      override implicit val monad: MonadAsyncError[F] = new CatsMonadAsyncError

      override def publisherToStream(p: Publisher[ByteBuffer]): Stream[F, Byte] =
        p.toStream[F].flatMap(buf => Stream.chunk(Chunk.byteBuffer(buf)))

      override def publisherToBytes(p: Publisher[ByteBuffer]): F[Array[Byte]] = {
        p.toStream[F]
          .compile
          .fold(ByteBuffer.allocate(0))(concatByteBuffers)
          .map(_.array())
      }

      override def publisherToFile(p: Publisher[ByteBuffer], f: File): F[Unit] = {
        p.toStream[F]
          .flatMap(b => Stream.emits(b.array()))
          .through(fs2.io.file.writeAll(f.toPath, Blocker.liftExecutionContext(ExecutionContext.global)))
          .compile
          .drain
      }

      override def compileWebSocketPipe(
          ws: WebSocket[F],
          pipe: Pipe[F, WebSocketFrame.Data[_], WebSocketFrame]
      ): F[Unit] = Fs2WebSockets.handleThroughPipe(ws)(pipe)
    }

  override protected val bodyToAHC: BodyToAHC[F, Fs2Streams[F]] = new BodyToAHC[F, Fs2Streams[F]] {
    override val streams: Fs2Streams[F] = Fs2Streams[F]

    override protected def streamToPublisher(s: Stream[F, Byte]): Publisher[ByteBuf] =
      s.chunks.map(c => Unpooled.wrappedBuffer(c.toArray)).toUnicastPublisher
  }

  override protected def createAsyncQueue[T]: F[AsyncQueue[F, T]] =
    webSocketBufferCapacity
      .fold(InspectableQueue.unbounded[F, T])(InspectableQueue.bounded)
      .map(new Fs2AsyncQueue(_))
}

object AsyncHttpClientFs2Backend {
  private def apply[F[_]: ConcurrentEffect: ContextShift](
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder,
      webSocketBufferCapacity: Option[Int]
  ): SttpBackend[F, Fs2Streams[F] with WebSockets] =
    new FollowRedirectsBackend(
      new AsyncHttpClientFs2Backend(asyncHttpClient, closeClient, customizeRequest, webSocketBufferCapacity)
    )

  def apply[F[_]: ConcurrentEffect: ContextShift](
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): F[SttpBackend[F, Fs2Streams[F] with WebSockets]] =
    implicitly[Sync[F]]
      .delay(
        apply[F](
          AsyncHttpClientBackend.defaultClient(options),
          closeClient = true,
          customizeRequest,
          webSocketBufferCapacity
        )
      )

  /**
    * Makes sure the backend is closed after usage.
    */
  def resource[F[_]: ConcurrentEffect: ContextShift](
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): Resource[F, SttpBackend[F, Fs2Streams[F] with WebSockets]] =
    Resource.make(apply(options, customizeRequest, webSocketBufferCapacity))(_.close())

  def usingConfig[F[_]: ConcurrentEffect: ContextShift](
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): F[SttpBackend[F, Fs2Streams[F] with WebSockets]] =
    implicitly[Sync[F]].delay(
      apply[F](new DefaultAsyncHttpClient(cfg), closeClient = true, customizeRequest, webSocketBufferCapacity)
    )

  /**
    * Makes sure the backend is closed after usage.
    */
  def resourceUsingConfig[F[_]: ConcurrentEffect: ContextShift](
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): Resource[F, SttpBackend[F, Fs2Streams[F] with WebSockets]] =
    Resource.make(usingConfig(cfg, customizeRequest, webSocketBufferCapacity))(_.close())

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def usingConfigBuilder[F[_]: ConcurrentEffect: ContextShift](
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): F[SttpBackend[F, Fs2Streams[F] with WebSockets]] =
    implicitly[Sync[F]].delay(
      AsyncHttpClientFs2Backend[F](
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true,
        customizeRequest,
        webSocketBufferCapacity
      )
    )

  /**
    * Makes sure the backend is closed after usage.
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def resourceUsingConfigBuilder[F[_]: ConcurrentEffect: ContextShift](
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): Resource[F, SttpBackend[F, Fs2Streams[F] with WebSockets]] =
    Resource.make(usingConfigBuilder(updateConfig, options, customizeRequest, webSocketBufferCapacity))(_.close())

  def usingClient[F[_]: ConcurrentEffect: ContextShift](
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): SttpBackend[F, Fs2Streams[F] with WebSockets] =
    apply[F](client, closeClient = false, customizeRequest, webSocketBufferCapacity)

  /**
    * Create a stub backend for testing, which uses the `F` response wrapper, and supports `Stream[F, ByteBuffer]`
    * streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Concurrent]: SttpBackendStub[F, Fs2Streams[F] with WebSockets] =
    SttpBackendStub(new CatsMonadAsyncError())
}
