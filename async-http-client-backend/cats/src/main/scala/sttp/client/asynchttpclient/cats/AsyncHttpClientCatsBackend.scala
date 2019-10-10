package sttp.client.asynchttpclient.cats

import java.io.{ByteArrayInputStream, File}
import java.nio.ByteBuffer

import cats.effect.implicits._
import cats.effect.{Async, ContextShift, Sync}
import io.netty.buffer.ByteBuf
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  BoundRequestBuilder,
  DefaultAsyncHttpClient,
  DefaultAsyncHttpClientConfig
}
import org.reactivestreams.Publisher
import sttp.client.asynchttpclient.{AsyncHttpClientBackend, WebSocketHandler}
import sttp.client.impl.cats.CatsMonadAsyncError
import sttp.client.internal.FileHelpers
import sttp.client.{FollowRedirectsBackend, Request, Response, SttpBackend, SttpBackendOptions}
import cats.implicits._

import scala.language.higherKinds

class AsyncHttpClientCatsBackend[F[_]: Async: ContextShift] private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean,
    customizeRequest: BoundRequestBuilder => BoundRequestBuilder
) extends AsyncHttpClientBackend[F, Nothing](asyncHttpClient, new CatsMonadAsyncError, closeClient, customizeRequest) {

  override def send[T](r: Request[T, Nothing]): F[Response[T]] = {
    super.send(r).guarantee(implicitly[ContextShift[F]].shift)
  }

  override protected def streamBodyToPublisher(s: Nothing): Publisher[ByteBuf] =
    s // nothing is everything

  override protected def publisherToStreamBody(p: Publisher[ByteBuffer]): Nothing =
    throw new IllegalStateException("This backend does not support streaming")

  override protected def publisherToFile(p: Publisher[ByteBuffer], f: File): F[Unit] = {
    publisherToBytes(p)
      .guarantee(implicitly[ContextShift[F]].shift)
      .map(bytes => FileHelpers.saveFile(f, new ByteArrayInputStream(bytes)))
  }
}

object AsyncHttpClientCatsBackend {

  private def apply[F[_]: Async: ContextShift](
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder
  ): SttpBackend[F, Nothing, WebSocketHandler] =
    new FollowRedirectsBackend[F, Nothing, WebSocketHandler](
      new AsyncHttpClientCatsBackend(asyncHttpClient, closeClient, customizeRequest)
    )

  /**
    * After sending a request, always shifts to the thread pool backing the given `ContextShift[F]`.
    */
  def apply[F[_]: Async: ContextShift](
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): F[SttpBackend[F, Nothing, WebSocketHandler]] =
    implicitly[Sync[F]]
      .delay(
        AsyncHttpClientCatsBackend(AsyncHttpClientBackend.defaultClient(options), closeClient = true, customizeRequest)
      )

  /**
    * After sending a request, always shifts to the thread pool backing the given `ContextShift[F]`.
    */
  def usingConfig[F[_]: Async: ContextShift](
      cfg: AsyncHttpClientConfig,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): F[SttpBackend[F, Nothing, WebSocketHandler]] =
    implicitly[Sync[F]]
      .delay(AsyncHttpClientCatsBackend(new DefaultAsyncHttpClient(cfg), closeClient = true, customizeRequest))

  /**
    * After sending a request, always shifts to the thread pool backing the given `ContextShift[F]`.
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def usingConfigBuilder[F[_]: Async: ContextShift](
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): F[SttpBackend[F, Nothing, WebSocketHandler]] =
    implicitly[Sync[F]].delay(
      AsyncHttpClientCatsBackend(
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true,
        customizeRequest
      )
    )

  /**
    * After sending a request, always shifts to the thread pool backing the given `ContextShift[F]`.
    */
  def usingClient[F[_]: Async: ContextShift](
      client: AsyncHttpClient,
      customizeRequest: BoundRequestBuilder => BoundRequestBuilder = identity
  ): SttpBackend[F, Nothing, WebSocketHandler] =
    AsyncHttpClientCatsBackend(client, closeClient = false, customizeRequest)
}
