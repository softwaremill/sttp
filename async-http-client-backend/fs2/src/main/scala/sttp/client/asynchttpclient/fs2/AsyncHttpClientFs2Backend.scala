package sttp.client.asynchttpclient.fs2

import java.io.File
import java.nio.ByteBuffer

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import fs2.Stream
import fs2.interop.reactivestreams._
import io.netty.buffer.{ByteBuf, Unpooled}
import org.asynchttpclient.{Request => _, Response => _, _}
import org.reactivestreams.Publisher
import sttp.client.asynchttpclient.{AsyncHttpClientBackend, WebSocketHandler}
import sttp.client.impl.cats.CatsMonadAsyncError
import sttp.client.internal._
import sttp.client.testing.SttpBackendStub
import sttp.client.ws.WebSocketResponse
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions, _}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class AsyncHttpClientFs2Backend[F[_]: ConcurrentEffect: ContextShift] private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder
) extends AsyncHttpClientBackend[F, Stream[F, ByteBuffer]](
      asyncHttpClient,
      new CatsMonadAsyncError,
      closeClient,
      customizeRequest
    ) {
  override def send[T](r: Request[T, Stream[F, ByteBuffer]]): F[Response[T]] = {
    super.send(r).guarantee(implicitly[ContextShift[F]].shift)
  }

  override def openWebsocket[T, WS_RESULT](
      r: Request[T, Stream[F, ByteBuffer]],
      handler: WebSocketHandler[WS_RESULT]
  ): F[WebSocketResponse[WS_RESULT]] = super.openWebsocket(r, handler).guarantee(ContextShift[F].shift)

  override protected def streamBodyToPublisher(s: Stream[F, ByteBuffer]): Publisher[ByteBuf] =
    s.map(Unpooled.wrappedBuffer).toUnicastPublisher

  override protected def publisherToStreamBody(p: Publisher[ByteBuffer]): Stream[F, ByteBuffer] =
    p.toStream[F]

  override protected def publisherToBytes(p: Publisher[ByteBuffer]): F[Array[Byte]] = {
    p.toStream[F]
      .compile
      .fold(ByteBuffer.allocate(0))(concatByteBuffers)
      .map(_.array())
  }

  override protected def publisherToFile(p: Publisher[ByteBuffer], f: File): F[Unit] = {
    p.toStream[F]
      .flatMap(b => Stream.emits(b.array()))
      .through(fs2.io.file.writeAll(f.toPath, Blocker.liftExecutionContext(ExecutionContext.Implicits.global)))
      .compile
      .drain
  }
}

object AsyncHttpClientFs2Backend {
  private def apply[F[_]: ConcurrentEffect: ContextShift](
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder
  ): SttpBackend[F, Stream[F, ByteBuffer], WebSocketHandler] =
    new FollowRedirectsBackend(new AsyncHttpClientFs2Backend(asyncHttpClient, closeClient, customizeRequest))

  def apply[F[_]: ConcurrentEffect: ContextShift](
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): F[SttpBackend[F, Stream[F, ByteBuffer], WebSocketHandler]] =
    implicitly[Sync[F]]
      .delay(apply[F](AsyncHttpClientBackend.defaultClient(options), closeClient = true, customizeRequest))

  /**
    * Makes sure the backend is closed after usage.
    */
  def resource[F[_]: ConcurrentEffect: ContextShift](
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Resource[F, SttpBackend[F, Stream[F, ByteBuffer], WebSocketHandler]] =
    Resource.make(apply(options, customizeRequest))(_.close())

  def usingConfig[F[_]: ConcurrentEffect: ContextShift](
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): F[SttpBackend[F, Stream[F, ByteBuffer], WebSocketHandler]] =
    implicitly[Sync[F]].delay(apply[F](new DefaultAsyncHttpClient(cfg), closeClient = true, customizeRequest))

  /**
    * Makes sure the backend is closed after usage.
    */
  def resourceUsingConfig[F[_]: ConcurrentEffect: ContextShift](
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Resource[F, SttpBackend[F, Stream[F, ByteBuffer], WebSocketHandler]] =
    Resource.make(usingConfig(cfg, customizeRequest))(_.close())

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def usingConfigBuilder[F[_]: ConcurrentEffect: ContextShift](
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): F[SttpBackend[F, Stream[F, ByteBuffer], WebSocketHandler]] =
    implicitly[Sync[F]].delay(
      AsyncHttpClientFs2Backend[F](
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true,
        customizeRequest
      )
    )

  /**
    * Makes sure the backend is closed after usage.
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def resourceUsingConfigBuilder[F[_]: ConcurrentEffect: ContextShift](
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): Resource[F, SttpBackend[F, Stream[F, ByteBuffer], WebSocketHandler]] =
    Resource.make(usingConfigBuilder(updateConfig, options, customizeRequest))(_.close())

  def usingClient[F[_]: ConcurrentEffect: ContextShift](
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): SttpBackend[F, Stream[F, ByteBuffer], WebSocketHandler] =
    apply[F](client, closeClient = false, customizeRequest)

  /**
    * Create a stub backend for testing, which uses the `F` response wrapper, and supports `Stream[F, ByteBuffer]`
    * streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Async]: SttpBackendStub[F, Stream[F, ByteBuffer]] = SttpBackendStub(new CatsMonadAsyncError())
}
