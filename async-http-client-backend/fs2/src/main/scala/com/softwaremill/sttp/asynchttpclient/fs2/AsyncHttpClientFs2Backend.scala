package com.softwaremill.sttp.asynchttpclient.fs2

import java.nio.ByteBuffer

import cats.effect._
import com.softwaremill.sttp.asynchttpclient.AsyncHttpClientBackend
import com.softwaremill.sttp.impl.cats.EffectMonadAsyncError
import com.softwaremill.sttp.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions, concatByteBuffers}
import fs2._
import fs2.interop.reactivestreams._
import io.netty.buffer.{ByteBuf, Unpooled}
import org.asynchttpclient.{AsyncHttpClient, AsyncHttpClientConfig, DefaultAsyncHttpClient}
import org.reactivestreams.Publisher

import scala.language.higherKinds

class AsyncHttpClientFs2Backend[F[_]: ConcurrentEffect] private (asyncHttpClient: AsyncHttpClient, closeClient: Boolean)(
    implicit t: Timer[F])
    extends AsyncHttpClientBackend[F, Stream[F, ByteBuffer]](
      asyncHttpClient,
      new EffectMonadAsyncError,
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

    implicitly[Effect[F]].map(bytes)(_.array())
  }
}

object AsyncHttpClientFs2Backend {

  private def apply[F[_]: ConcurrentEffect](asyncHttpClient: AsyncHttpClient, closeClient: Boolean)(
      implicit t: Timer[F]): SttpBackend[F, Stream[F, ByteBuffer]] =
    new FollowRedirectsBackend(new AsyncHttpClientFs2Backend(asyncHttpClient, closeClient))

  def apply[F[_]: ConcurrentEffect](options: SttpBackendOptions = SttpBackendOptions.Default)(
      implicit t: Timer[F]): SttpBackend[F, Stream[F, ByteBuffer]] =
    AsyncHttpClientFs2Backend[F](AsyncHttpClientBackend.defaultClient(options), closeClient = true)

  def usingConfig[F[_]: ConcurrentEffect](cfg: AsyncHttpClientConfig)(
      implicit t: Timer[F]): SttpBackend[F, Stream[F, ByteBuffer]] =
    AsyncHttpClientFs2Backend[F](new DefaultAsyncHttpClient(cfg), closeClient = true)

  def usingClient[F[_]: ConcurrentEffect](client: AsyncHttpClient)(
      implicit t: Timer[F]): SttpBackend[F, Stream[F, ByteBuffer]] =
    AsyncHttpClientFs2Backend[F](client, closeClient = false)
}
