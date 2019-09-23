package sttp.client.asynchttpclient.fs2

import java.nio.ByteBuffer

import cats.Functor
import cats.effect._
import fs2._
import fs2.interop.reactivestreams._
import io.netty.buffer.{ByteBuf, Unpooled}
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  DefaultAsyncHttpClient,
  DefaultAsyncHttpClientConfig
}
import org.reactivestreams.Publisher
import sttp.client.asynchttpclient.AsyncHttpClientBackend
import sttp.client.impl.cats.CatsMonadAsyncError
import sttp.client.internal._
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}

import scala.language.higherKinds

class AsyncHttpClientFs2Backend[F[_]: ConcurrentEffect] private (asyncHttpClient: AsyncHttpClient, closeClient: Boolean)
    extends AsyncHttpClientBackend[F, Stream[F, ByteBuffer]](
      asyncHttpClient,
      new CatsMonadAsyncError,
      closeClient
    ) {

  override protected def streamBodyToPublisher(s: Stream[F, ByteBuffer]): Publisher[ByteBuf] =
    s.map(Unpooled.wrappedBuffer).toUnicastPublisher

  override protected def publisherToStreamBody(p: Publisher[ByteBuffer]): Stream[F, ByteBuffer] =
    p.toStream[F]

  override protected def publisherToBytes(p: Publisher[ByteBuffer]): F[Array[Byte]] = {
    val bytes = p
      .toStream[F]
      .compile
      .fold(ByteBuffer.allocate(0))(concatByteBuffers)

    Functor[F].map(bytes)(_.array())
  }
}

object AsyncHttpClientFs2Backend {

  private def apply[F[_]: ConcurrentEffect](
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean
  ): SttpBackend[F, Stream[F, ByteBuffer]] =
    new FollowRedirectsBackend(new AsyncHttpClientFs2Backend(asyncHttpClient, closeClient))

  def apply[F[_]: ConcurrentEffect](
      options: SttpBackendOptions = SttpBackendOptions.Default
  ): F[SttpBackend[F, Stream[F, ByteBuffer]]] =
    implicitly[Sync[F]].delay(apply[F](AsyncHttpClientBackend.defaultClient(options), closeClient = true))

  def usingConfig[F[_]: ConcurrentEffect](cfg: AsyncHttpClientConfig): F[SttpBackend[F, Stream[F, ByteBuffer]]] =
    implicitly[Sync[F]].delay(apply[F](new DefaultAsyncHttpClient(cfg), closeClient = true))

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def usingConfigBuilder[F[_]: ConcurrentEffect](
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default
  ): F[SttpBackend[F, Stream[F, ByteBuffer]]] =
    implicitly[Sync[F]].delay(
      AsyncHttpClientFs2Backend[F](
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true
      )
    )

  def usingClient[F[_]: ConcurrentEffect](client: AsyncHttpClient): SttpBackend[F, Stream[F, ByteBuffer]] =
    apply[F](client, closeClient = false)
}
