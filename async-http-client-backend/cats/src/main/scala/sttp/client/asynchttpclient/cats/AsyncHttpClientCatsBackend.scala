package sttp.client.asynchttpclient.cats

import java.nio.ByteBuffer

import cats.effect.implicits._
import cats.effect.{Async, ContextShift, Sync}
import io.netty.buffer.ByteBuf
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  DefaultAsyncHttpClient,
  DefaultAsyncHttpClientConfig
}
import org.reactivestreams.Publisher
import sttp.client.asynchttpclient.AsyncHttpClientBackend
import sttp.client.impl.cats.CatsMonadAsyncError
import sttp.client.{FollowRedirectsBackend, Request, Response, SttpBackend, SttpBackendOptions}

import scala.language.higherKinds

class AsyncHttpClientCatsBackend[F[_]: Async: ContextShift] private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean
) extends AsyncHttpClientBackend[F, Nothing](
      asyncHttpClient,
      new CatsMonadAsyncError,
      closeClient
    ) {

  override def send[T](r: Request[T, Nothing]): F[Response[T]] = {
    super.send(r).guarantee(implicitly[ContextShift[F]].shift)
  }

  override protected def streamBodyToPublisher(s: Nothing): Publisher[ByteBuf] =
    s // nothing is everything

  override protected def publisherToStreamBody(p: Publisher[ByteBuffer]): Nothing =
    throw new IllegalStateException("This backend does not support streaming")

  override protected def publisherToBytes(p: Publisher[ByteBuffer]): F[Array[Byte]] =
    throw new IllegalStateException("This backend does not support streaming")
}

object AsyncHttpClientCatsBackend {

  private def apply[F[_]: Async: ContextShift](
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean
  ): SttpBackend[F, Nothing] =
    new FollowRedirectsBackend[F, Nothing](new AsyncHttpClientCatsBackend(asyncHttpClient, closeClient))

  /**
    * After sending a request, always shifts to the thread pool backing the given `ContextShift[F]`.
    */
  def apply[F[_]: Async: ContextShift](
      options: SttpBackendOptions = SttpBackendOptions.Default
  ): F[SttpBackend[F, Nothing]] =
    implicitly[Sync[F]]
      .delay(AsyncHttpClientCatsBackend(AsyncHttpClientBackend.defaultClient(options), closeClient = true))

  /**
    * After sending a request, always shifts to the thread pool backing the given `ContextShift[F]`.
    */
  def usingConfig[F[_]: Async: ContextShift](cfg: AsyncHttpClientConfig): F[SttpBackend[F, Nothing]] =
    implicitly[Sync[F]].delay(AsyncHttpClientCatsBackend(new DefaultAsyncHttpClient(cfg), closeClient = true))

  /**
    * After sending a request, always shifts to the thread pool backing the given `ContextShift[F]`.
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def usingConfigBuilder[F[_]: Async: ContextShift](
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default
  ): F[SttpBackend[F, Nothing]] =
    implicitly[Sync[F]].delay(
      AsyncHttpClientCatsBackend(
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true
      )
    )

  /**
    * After sending a request, always shifts to the thread pool backing the given `ContextShift[F]`.
    */
  def usingClient[F[_]: Async: ContextShift](client: AsyncHttpClient): SttpBackend[F, Nothing] =
    AsyncHttpClientCatsBackend(client, closeClient = false)
}
