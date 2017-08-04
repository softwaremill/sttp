package com.softwaremill.sttp.asynchttpclient.cats

import java.nio.ByteBuffer

import cats.effect._
import com.softwaremill.sttp.asynchttpclient.AsyncHttpClientHandler
import com.softwaremill.sttp.{MonadAsyncError, SttpHandler}
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  DefaultAsyncHttpClient
}
import org.reactivestreams.Publisher

import scala.language.higherKinds

class CatsAsyncHttpClientHandler[F[_]: Async](asyncHttpClient: AsyncHttpClient,
                                              closeClient: Boolean)
    extends AsyncHttpClientHandler[F, Nothing](asyncHttpClient,
                                               new AsyncMonad,
                                               closeClient) {
  override protected def streamBodyToPublisher(
      s: Nothing): Publisher[ByteBuffer] = s // nothing is everything

  override protected def publisherToStreamBody(
      p: Publisher[ByteBuffer]): Nothing =
    throw new IllegalStateException("This handler does not support streaming")
}

object CatsAsyncHttpClientHandler {

  def apply[F[_]: Async](): SttpHandler[F, Nothing] =
    new CatsAsyncHttpClientHandler(new DefaultAsyncHttpClient(),
                                   closeClient = true)

  def usingConfig[F[_]: Async](
      cfg: AsyncHttpClientConfig): SttpHandler[F, Nothing] =
    new CatsAsyncHttpClientHandler(new DefaultAsyncHttpClient(cfg),
                                   closeClient = true)

  def usingClient[F[_]: Async](
      client: AsyncHttpClient): SttpHandler[F, Nothing] =
    new CatsAsyncHttpClientHandler(client, closeClient = false)
}

private[cats] class AsyncMonad[F[_]](implicit F: Async[F])
    extends MonadAsyncError[F] {

  override def async[T](
      register: ((Either[Throwable, T]) => Unit) => Unit): F[T] =
    F.async(register)

  override def unit[T](t: T): F[T] = F.pure(t)

  override def map[T, T2](fa: F[T], f: (T) => T2): F[T2] = F.map(fa)(f)

  override def flatMap[T, T2](fa: F[T], f: (T) => F[T2]): F[T2] =
    F.flatMap(fa)(f)

  override def error[T](t: Throwable): F[T] = F.raiseError(t)
}
