package sttp.client3.asynchttpclient.fs2

import cats.effect.kernel._
import cats.effect.std.{Dispatcher, Queue}
import cats.implicits._
import fs2.interop.reactivestreams._
import fs2.io.file.Files
import fs2.{Chunk, Pipe, Stream}
import io.netty.buffer.{ByteBuf, Unpooled}
import org.asynchttpclient.{Request => _, Response => _, _}
import org.reactivestreams.Publisher
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.asynchttpclient.{AsyncHttpClientBackend, BodyFromAHC, BodyToAHC}
import sttp.client3.impl.cats.CatsMonadAsyncError
import sttp.client3.impl.fs2.{Fs2SimpleQueue, Fs2WebSockets}
import sttp.client3.internal._
import sttp.client3.internal.ws.SimpleQueue
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import sttp.monad.MonadAsyncError
import sttp.ws.{WebSocket, WebSocketFrame}

import java.io.File
import java.nio.ByteBuffer

class AsyncHttpClientFs2Backend[F[_]: Async] private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder,
    webSocketBufferCapacity: Option[Int],
    dispatcher: Dispatcher[F]
) extends AsyncHttpClientBackend[F, Fs2Streams[F], Fs2Streams[F] with WebSockets](
      asyncHttpClient,
      new CatsMonadAsyncError,
      closeClient,
      customizeRequest
    ) {

  override val streams: Fs2Streams[F] = Fs2Streams[F]

  override protected val bodyFromAHC: BodyFromAHC[F, Fs2Streams[F]] =
    new BodyFromAHC[F, Fs2Streams[F]] {
      override val streams: Fs2Streams[F] = Fs2Streams[F]
      override implicit val monad: MonadAsyncError[F] = new CatsMonadAsyncError

      override def publisherToStream(p: Publisher[ByteBuffer]): Stream[F, Byte] =
        p.toStream[F].flatMap(buf => Stream.chunk(Chunk.byteBuffer(buf)))

      override def publisherToBytes(p: Publisher[ByteBuffer]): F[Array[Byte]] = {
        p.toStream[F]
          .compile
          .fold(scala.collection.immutable.Queue.empty[Array[Byte]])(enqueueBytes)
          .map(concatBytes)
      }

      override def publisherToFile(p: Publisher[ByteBuffer], f: File): F[Unit] = {
        p.toStream[F]
          .flatMap(b => Stream.emits(b.array()))
          .through(Files[F].writeAll(f.toPath))
          .compile
          .drain
      }

      override def bytesToPublisher(b: Array[Byte]): F[Publisher[ByteBuffer]] =
        (StreamUnicastPublisher(Stream.apply[F, ByteBuffer](ByteBuffer.wrap(b)), dispatcher): Publisher[ByteBuffer])
          .pure[F]

      override def fileToPublisher(f: File): F[Publisher[ByteBuffer]] = {
        val stream =
          Files[F]
            .readAll(f.toPath, IOBufferSize)
            .mapChunks(c => Chunk(ByteBuffer.wrap(c.toArray)))
        (StreamUnicastPublisher(stream, dispatcher): Publisher[ByteBuffer]).pure[F]
      }

      override def compileWebSocketPipe(
          ws: WebSocket[F],
          pipe: Pipe[F, WebSocketFrame.Data[_], WebSocketFrame]
      ): F[Unit] = Fs2WebSockets.handleThroughPipe(ws)(pipe)
    }

  override protected val bodyToAHC: BodyToAHC[F, Fs2Streams[F]] = new BodyToAHC[F, Fs2Streams[F]] {
    override val streams: Fs2Streams[F] = Fs2Streams[F]

    override protected def streamToPublisher(s: Stream[F, Byte]): Publisher[ByteBuf] =
      (StreamUnicastPublisher(s.chunks.map(c => Unpooled.wrappedBuffer(c.toArray)), dispatcher): Publisher[ByteBuf])
  }

  override protected def createSimpleQueue[T]: F[SimpleQueue[F, T]] =
    webSocketBufferCapacity
      .fold(Queue.unbounded[F, T])(Queue.bounded)
      .map(new Fs2SimpleQueue(_, webSocketBufferCapacity, dispatcher))
}

object AsyncHttpClientFs2Backend {
  private def apply[F[_]: Async](
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder,
      webSocketBufferCapacity: Option[Int],
      dispatcher: Dispatcher[F]
  ): SttpBackend[F, Fs2Streams[F] with WebSockets] =
    new FollowRedirectsBackend(
      new AsyncHttpClientFs2Backend(asyncHttpClient, closeClient, customizeRequest, webSocketBufferCapacity, dispatcher)
    )

  def apply[F[_]: Async](
      dispatcher: Dispatcher[F],
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): F[SttpBackend[F, Fs2Streams[F] with WebSockets]] =
    Sync[F]
      .delay(
        apply[F](
          AsyncHttpClientBackend.defaultClient(options),
          closeClient = true,
          customizeRequest,
          webSocketBufferCapacity,
          dispatcher
        )
      )

  /** Makes sure the backend is closed after usage. */
  def resource[F[_]: Async](
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): Resource[F, SttpBackend[F, Fs2Streams[F] with WebSockets]] =
    Dispatcher[F].flatMap(dispatcher =>
      Resource.make(apply(dispatcher, options, customizeRequest, webSocketBufferCapacity))(_.close())
    )

  def usingConfig[F[_]: Async](
      cfg: AsyncHttpClientConfig,
      dispatcher: Dispatcher[F],
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): F[SttpBackend[F, Fs2Streams[F] with WebSockets]] =
    Sync[F].delay(
      apply[F](
        new DefaultAsyncHttpClient(cfg),
        closeClient = true,
        customizeRequest,
        webSocketBufferCapacity,
        dispatcher
      )
    )

  /** Makes sure the backend is closed after usage. */
  def resourceUsingConfig[F[_]: Async](
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): Resource[F, SttpBackend[F, Fs2Streams[F] with WebSockets]] =
    Dispatcher[F].flatMap(dispatcher =>
      Resource.make(usingConfig(cfg, dispatcher, customizeRequest, webSocketBufferCapacity))(_.close())
    )

  /** @param updateConfig A function which updates the default configuration (created basing on `options`). */
  def usingConfigBuilder[F[_]: Async](
      dispatcher: Dispatcher[F],
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): F[SttpBackend[F, Fs2Streams[F] with WebSockets]] =
    Sync[F].delay(
      AsyncHttpClientFs2Backend[F](
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true,
        customizeRequest,
        webSocketBufferCapacity,
        dispatcher
      )
    )

  /** Makes sure the backend is closed after usage.
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def resourceUsingConfigBuilder[F[_]: Async](
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): Resource[F, SttpBackend[F, Fs2Streams[F] with WebSockets]] =
    Dispatcher[F].flatMap(dispatcher =>
      Resource.make(usingConfigBuilder(dispatcher, updateConfig, options, customizeRequest, webSocketBufferCapacity))(
        _.close()
      )
    )

  def usingClient[F[_]: Async](
      client: AsyncHttpClient,
      dispatcher: Dispatcher[F],
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      webSocketBufferCapacity: Option[Int] = AsyncHttpClientBackend.DefaultWebSocketBufferCapacity
  ): SttpBackend[F, Fs2Streams[F] with WebSockets] =
    apply[F](client, closeClient = false, customizeRequest, webSocketBufferCapacity, dispatcher)

  /** Create a stub backend for testing, which uses the `F` response wrapper, and supports `Stream[F, ByteBuffer]`
    * streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Async]: SttpBackendStub[F, Fs2Streams[F] with WebSockets] =
    SttpBackendStub(new CatsMonadAsyncError())
}
