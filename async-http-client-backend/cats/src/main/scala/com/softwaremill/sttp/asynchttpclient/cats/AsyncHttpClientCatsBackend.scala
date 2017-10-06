package com.softwaremill.sttp.asynchttpclient.cats

import java.nio.ByteBuffer

import cats.effect._
import com.softwaremill.sttp.asynchttpclient.AsyncHttpClientBackend
import com.softwaremill.sttp.{
  FollowRedirectsBackend,
  MonadAsyncError,
  SttpBackend,
  SttpBackendOptions
}
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  DefaultAsyncHttpClient
}
import org.reactivestreams.Publisher

import scala.language.higherKinds

class AsyncHttpClientCatsBackend[F[_]: Async] private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean
) extends AsyncHttpClientBackend[F, Nothing](
      asyncHttpClient,
      new AsyncMonad,
      closeClient
    ) {
  override protected def streamBodyToPublisher(
      s: Nothing): Publisher[ByteBuffer] = s // nothing is everything

  override protected def publisherToStreamBody(
      p: Publisher[ByteBuffer]): Nothing =
    throw new IllegalStateException("This backend does not support streaming")

  override protected def publisherToString(
      p: Publisher[ByteBuffer]): F[String] =
    throw new IllegalStateException("This backend does not support streaming")
}

object AsyncHttpClientCatsBackend {

  private def apply[F[_]: Async](
      asyncHttpClient: AsyncHttpClient,
      closeClient: Boolean): SttpBackend[F, Nothing] =
    new FollowRedirectsBackend[F, Nothing](
      new AsyncHttpClientCatsBackend(asyncHttpClient, closeClient))

  def apply[F[_]: Async](
      options: SttpBackendOptions = SttpBackendOptions.Default)
    : SttpBackend[F, Nothing] =
    AsyncHttpClientCatsBackend(AsyncHttpClientBackend.defaultClient(options),
                               closeClient = true)

  def usingConfig[F[_]: Async](
      cfg: AsyncHttpClientConfig): SttpBackend[F, Nothing] =
    AsyncHttpClientCatsBackend(new DefaultAsyncHttpClient(cfg),
                               closeClient = true)

  def usingClient[F[_]: Async](
      client: AsyncHttpClient): SttpBackend[F, Nothing] =
    AsyncHttpClientCatsBackend(client, closeClient = false)
}

private[cats] class AsyncMonad[F[_]](implicit F: Async[F])
    extends MonadAsyncError[F] {

  override def async[T](
      register: ((Either[Throwable, T]) => Unit) => Unit): F[T] =
    F.async(register)

  override def unit[T](t: T): F[T] = F.pure(t)

  override def map[T, T2](fa: F[T])(f: (T) => T2): F[T2] = F.map(fa)(f)

  override def flatMap[T, T2](fa: F[T])(f: (T) => F[T2]): F[T2] =
    F.flatMap(fa)(f)

  override def error[T](t: Throwable): F[T] = F.raiseError(t)
}
