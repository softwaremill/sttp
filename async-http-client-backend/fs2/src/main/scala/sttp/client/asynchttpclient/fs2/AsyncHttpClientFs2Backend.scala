package sttp.client.asynchttpclient.fs2

import java.io.File
import java.nio.ByteBuffer

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import io.netty.buffer.{ByteBuf, Unpooled}
import fs2.Stream
import fs2.interop.reactivestreams._
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
import sttp.client._
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class AsyncHttpClientFs2Backend[F[_]: ConcurrentEffect: ContextShift] private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean
) extends AsyncHttpClientBackend[F, Stream[F, ByteBuffer]](
      asyncHttpClient,
      new CatsMonadAsyncError,
      closeClient
    ) {

  override def send[T](r: Request[T, Stream[F, ByteBuffer]]): F[Response[T]] = {
    super.send(r).guarantee(implicitly[ContextShift[F]].shift)
  }

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
      closeClient: Boolean
  ): SttpBackend[F, Stream[F, ByteBuffer]] =
    new FollowRedirectsBackend(new AsyncHttpClientFs2Backend(asyncHttpClient, closeClient))

  def apply[F[_]: ConcurrentEffect: ContextShift](
      options: SttpBackendOptions = SttpBackendOptions.Default
  ): F[SttpBackend[F, Stream[F, ByteBuffer]]] =
    implicitly[Sync[F]].delay(apply[F](AsyncHttpClientBackend.defaultClient(options), closeClient = true))

  def usingConfig[F[_]: ConcurrentEffect: ContextShift](
      cfg: AsyncHttpClientConfig
  ): F[SttpBackend[F, Stream[F, ByteBuffer]]] =
    implicitly[Sync[F]].delay(apply[F](new DefaultAsyncHttpClient(cfg), closeClient = true))

  /**
    * @param updateConfig A function which updates the default configuration (created basing on `options`).
    */
  def usingConfigBuilder[F[_]: ConcurrentEffect: ContextShift](
      updateConfig: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder,
      options: SttpBackendOptions = SttpBackendOptions.Default
  ): F[SttpBackend[F, Stream[F, ByteBuffer]]] =
    implicitly[Sync[F]].delay(
      AsyncHttpClientFs2Backend[F](
        AsyncHttpClientBackend.clientWithModifiedOptions(options, updateConfig),
        closeClient = true
      )
    )

  def usingClient[F[_]: ConcurrentEffect: ContextShift](
      client: AsyncHttpClient
  ): SttpBackend[F, Stream[F, ByteBuffer]] =
    apply[F](client, closeClient = false)
}
