package sttp.client3.asynchttpclient.cats

import java.io.{ByteArrayInputStream, File}
import java.nio.ByteBuffer

import cats.effect.implicits._
import cats.effect.{Concurrent, ContextShift, Resource, Sync}
import io.netty.buffer.ByteBuf
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  BoundRequestBuilder,
  DefaultAsyncHttpClient,
  DefaultAsyncHttpClientConfig
}
import org.reactivestreams.Publisher
import sttp.client3.asynchttpclient.{AsyncHttpClientBackend, BodyFromAHC, BodyToAHC}
import sttp.client3.impl.cats.CatsMonadAsyncError
import sttp.client3.internal.{FileHelpers, NoStreams}
import sttp.client3.{FollowRedirectsBackend, Request, Response, SttpBackend, SttpBackendOptions}
import cats.implicits._
import sttp.client3.FollowRedirectsBackend.UriEncoder
import sttp.client3.internal.ws.SimpleQueue
import sttp.client3.testing.SttpBackendStub
import sttp.monad.MonadAsyncError
import sttp.ws.WebSocket

class AsyncHttpClientCatsBackend[F[_]: Concurrent: ContextShift] private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder
) extends AsyncHttpClientBackend[F, Nothing, Any](
      asyncHttpClient,
      new CatsMonadAsyncError,
      closeClient,
      customizeRequest
    ) {

  override val streams: NoStreams = NoStreams

  override def send[T, R >: Any with sttp.capabilities.Effect[F]](r: Request[T, R]): F[Response[T]] = {
    super.send(r).guarantee(implicitly[ContextShift[F]].shift)
  }

  override protected val bodyFromAHC: BodyFromAHC[F, Nothing] = new BodyFromAHC[F, Nothing] {
    override val streams: NoStreams = NoStreams
    override implicit val monad: MonadAsyncError[F] = new CatsMonadAsyncError
    override def publisherToStream(p: Publisher[ByteBuffer]): Nothing =
      throw new IllegalStateException("This backend does not support streaming")
    override def compileWebSocketPipe(ws: WebSocket[F], pipe: Nothing): F[Unit] = pipe // nothing is everything

    override def publisherToFile(p: Publisher[ByteBuffer], f: File): F[Unit] = {
      publisherToBytes(p)
        .guarantee(implicitly[ContextShift[F]].shift)
        .map(bytes => FileHelpers.saveFile(f, new ByteArrayInputStream(bytes)))
    }
  }

  override protected def bodyToAHC: BodyToAHC[F, Nothing] =
    new BodyToAHC[F, Nothing] {
      override val streams: NoStreams = NoStreams
      override protected def streamToPublisher(s: Nothing): Publisher[ByteBuf] = s // nothing is everything
    }

  override protected def createSimpleQueue[T]: F[SimpleQueue[F, T]] =
    throw new IllegalStateException("Web sockets are not supported!")
}

object AsyncHttpClientCatsBackend {
  private def apply[F[_]: Concurrent: ContextShift](
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder,
      uriEncoder: UriEncoder
  ): SttpBackend[F, Any] =
    new FollowRedirectsBackend[F, Any](
      new AsyncHttpClientCatsBackend(asyncHttpClient, closeClient, customizeRequest),
      uriEncoder = uriEncoder
    )

  /** After sending a request, always shifts to the thread pool backing the given `ContextShift[F]`.
    */
  def apply[F[_]: Concurrent: ContextShift](
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      uriEncoder: UriEncoder = UriEncoder.DefaultEncoder
  ): F[SttpBackend[F, Any]] =
    Sync[F].delay(
      AsyncHttpClientCatsBackend(
        AsyncHttpClientBackend.defaultClient(options),
        closeClient = true,
        customizeRequest,
        uriEncoder
      )
    )

  /** Makes sure the backend is closed after usage. After sending a request, always shifts to the thread pool backing
    * the given `ContextShift[F]`.
    */
  def resource[F[_]: Concurrent: ContextShift](
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      uriEncoder: UriEncoder = UriEncoder.DefaultEncoder
  ): Resource[F, SttpBackend[F, Any]] =
    Resource.make(apply(options, customizeRequest, uriEncoder))(_.close())

  /** After sending a request, always shifts to the thread pool backing the given `ContextShift[F]`.
    */
  def usingConfig[F[_]: Concurrent: ContextShift](
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      uriEncoder: UriEncoder = UriEncoder.DefaultEncoder
  ): F[SttpBackend[F, Any]] =
    Sync[F].delay(
      AsyncHttpClientCatsBackend(new DefaultAsyncHttpClient(cfg), closeClient = true, customizeRequest, uriEncoder)
    )

  /** Makes sure the backend is closed after usage. After sending a request, always shifts to the thread pool backing
    * the given `ContextShift[F]`.
    */
  def resourceUsingConfig[F[_]: Concurrent: ContextShift](
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      uriEncoder: UriEncoder = UriEncoder.DefaultEncoder
  ): Resource[F, SttpBackend[F, Any]] =
    Resource.make(usingConfig(cfg, customizeRequest, uriEncoder))(_.close())

  /** After sending a request, always shifts to the thread pool backing the given `ContextShift[F]`.
    * @param updateConfig
    *   A function which updates the default configuration (created basing on `options`).
    */
  def usingConfigBuilder[F[_]: Concurrent: ContextShift](
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      uriEncoder: UriEncoder = UriEncoder.DefaultEncoder
  ): F[SttpBackend[F, Any]] =
    Sync[F].delay(
      AsyncHttpClientCatsBackend(
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true,
        customizeRequest,
        uriEncoder
      )
    )

  /** Makes sure the backend is closed after usage. After sending a request, always shifts to the thread pool backing
    * the given `ContextShift[F]`.
    * @param updateConfig
    *   A function which updates the default configuration (created basing on `options`).
    */
  def resourceUsingConfigBuilder[F[_]: Concurrent: ContextShift](
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      uriEncoder: UriEncoder = UriEncoder.DefaultEncoder
  ): Resource[F, SttpBackend[F, Any]] =
    Resource.make(usingConfigBuilder(updateConfig, options, customizeRequest, uriEncoder))(_.close())

  /** After sending a request, always shifts to the thread pool backing the given `ContextShift[F]`.
    */
  def usingClient[F[_]: Concurrent: ContextShift](
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity,
      uriEncoder: UriEncoder = UriEncoder.DefaultEncoder
  ): SttpBackend[F, Any] =
    AsyncHttpClientCatsBackend(client, closeClient = false, customizeRequest, uriEncoder)

  /** Create a stub backend for testing, which uses the `F` response wrapper, and doesn't support streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Concurrent]: SttpBackendStub[F, Any] = SttpBackendStub(new CatsMonadAsyncError())
}
