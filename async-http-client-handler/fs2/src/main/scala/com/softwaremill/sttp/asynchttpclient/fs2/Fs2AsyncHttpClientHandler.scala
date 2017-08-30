package com.softwaremill.sttp.asynchttpclient.fs2

import java.nio.ByteBuffer

import cats.effect._
import com.softwaremill.sttp.asynchttpclient.AsyncHttpClientHandler
import com.softwaremill.sttp.{MonadAsyncError, SttpHandler}
import fs2._
import fs2.interop.reactivestreams._
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  DefaultAsyncHttpClient
}
import org.reactivestreams.Publisher

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

class Fs2AsyncHttpClientHandler[F[_]: Effect] private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean)(implicit ec: ExecutionContext)
    extends AsyncHttpClientHandler[F, Stream[F, ByteBuffer]](
      asyncHttpClient,
      new EffectMonad,
      closeClient
    ) {

  override protected def streamBodyToPublisher(
      s: Stream[F, ByteBuffer]): Publisher[ByteBuffer] =
    s.toUnicastPublisher

  override protected def publisherToStreamBody(
      p: Publisher[ByteBuffer]): Stream[F, ByteBuffer] =
    p.toStream[F]
}

object Fs2AsyncHttpClientHandler {

  /**
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def apply[F[_]: Effect]()(
      implicit ec: ExecutionContext = ExecutionContext.Implicits.global)
    : SttpHandler[F, Stream[F, ByteBuffer]] =
    new Fs2AsyncHttpClientHandler[F](new DefaultAsyncHttpClient(),
                                     closeClient = true)

  /**
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def usingConfig[F[_]: Effect](cfg: AsyncHttpClientConfig)(
      implicit ec: ExecutionContext = ExecutionContext.Implicits.global)
    : SttpHandler[F, Stream[F, ByteBuffer]] =
    new Fs2AsyncHttpClientHandler[F](new DefaultAsyncHttpClient(cfg),
                                     closeClient = true)

  /**
    * @param ec The execution context for running non-network related operations,
    *           e.g. mapping responses. Defaults to the global execution
    *           context.
    */
  def usingClient[F[_]: Effect](client: AsyncHttpClient)(
      implicit ec: ExecutionContext = ExecutionContext.Implicits.global)
    : SttpHandler[F, Stream[F, ByteBuffer]] =
    new Fs2AsyncHttpClientHandler[F](client, closeClient = false)
}

private[fs2] class EffectMonad[F[_]](implicit F: Effect[F])
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
