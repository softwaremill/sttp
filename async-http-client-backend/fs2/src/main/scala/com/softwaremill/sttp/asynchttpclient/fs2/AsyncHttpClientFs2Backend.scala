package com.softwaremill.sttp.asynchttpclient.fs2

import java.nio.ByteBuffer

import cats.effect._
import com.softwaremill.sttp.asynchttpclient.AsyncHttpClientBackend
import com.softwaremill.sttp.{
  FollowRedirectsBackend,
  MonadAsyncError,
  SttpBackend,
  SttpBackendOptions,
  Utf8,
  concatByteBuffers
}
import fs2._
import fs2.interop.reactivestreams._
import io.netty.buffer.{ByteBuf, Unpooled}
import org.asynchttpclient.{AsyncHttpClient, AsyncHttpClientConfig, DefaultAsyncHttpClient}
import org.reactivestreams.Publisher

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class AsyncHttpClientFs2Backend[F[_]: Effect] private (asyncHttpClient: AsyncHttpClient, closeClient: Boolean)(
    implicit ec: ExecutionContext)
    extends AsyncHttpClientBackend[F, Stream[F, ByteBuffer]](
      asyncHttpClient,
      new EffectMonad,
      closeClient
    ) {

  override protected def streamBodyToPublisher(s: Stream[F, ByteBuffer]): Publisher[ByteBuf] =
    s.map(Unpooled.wrappedBuffer).toUnicastPublisher

  override protected def publisherToStreamBody(p: Publisher[ByteBuffer]): Stream[F, ByteBuffer] =
    p.toStream[F]

  override protected def publisherToString(p: Publisher[ByteBuffer]): F[String] = {
    val bytes = p
      .toStream[F]
      .compile
      .fold(ByteBuffer.allocate(0))(concatByteBuffers)

    implicitly[Effect[F]].map(bytes)(bb => new String(bb.array(), Utf8))
  }
}

object AsyncHttpClientFs2Backend {

  private def apply[F[_]: Effect](asyncHttpClient: AsyncHttpClient, closeClient: Boolean)(
      implicit ec: ExecutionContext): SttpBackend[F, Stream[F, ByteBuffer]] =
    new FollowRedirectsBackend(new AsyncHttpClientFs2Backend(asyncHttpClient, closeClient))

  /**
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def apply[F[_]: Effect](options: SttpBackendOptions = SttpBackendOptions.Default)(
      implicit ec: ExecutionContext = ExecutionContext.Implicits.global): SttpBackend[F, Stream[F, ByteBuffer]] =
    AsyncHttpClientFs2Backend[F](AsyncHttpClientBackend.defaultClient(options), closeClient = true)

  /**
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def usingConfig[F[_]: Effect](cfg: AsyncHttpClientConfig)(
      implicit ec: ExecutionContext = ExecutionContext.Implicits.global): SttpBackend[F, Stream[F, ByteBuffer]] =
    AsyncHttpClientFs2Backend[F](new DefaultAsyncHttpClient(cfg), closeClient = true)

  /**
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def usingClient[F[_]: Effect](client: AsyncHttpClient)(
      implicit ec: ExecutionContext = ExecutionContext.Implicits.global): SttpBackend[F, Stream[F, ByteBuffer]] =
    AsyncHttpClientFs2Backend[F](client, closeClient = false)
}

private[fs2] class EffectMonad[F[_]](implicit F: Effect[F]) extends MonadAsyncError[F] {

  override def async[T](register: ((Either[Throwable, T]) => Unit) => Unit): F[T] =
    F.async(register)

  override def unit[T](t: T): F[T] = F.pure(t)

  override def map[T, T2](fa: F[T])(f: (T) => T2): F[T2] = F.map(fa)(f)

  override def flatMap[T, T2](fa: F[T])(f: (T) => F[T2]): F[T2] =
    F.flatMap(fa)(f)

  override def error[T](t: Throwable): F[T] = F.raiseError(t)

  override protected def handleWrappedError[T](rt: F[T])(h: PartialFunction[Throwable, F[T]]): F[T] =
    F.recoverWith(rt)(h)
}
