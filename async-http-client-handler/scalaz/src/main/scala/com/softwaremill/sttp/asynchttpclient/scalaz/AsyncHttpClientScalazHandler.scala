package com.softwaremill.sttp.asynchttpclient.scalaz

import java.nio.ByteBuffer

import com.softwaremill.sttp.{MonadAsyncError, SttpHandler}
import com.softwaremill.sttp.asynchttpclient.AsyncHttpClientHandler
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  DefaultAsyncHttpClient
}
import org.reactivestreams.Publisher

import scalaz.{-\/, \/-}
import scalaz.concurrent.Task

class AsyncHttpClientScalazHandler private (asyncHttpClient: AsyncHttpClient,
                                            closeClient: Boolean)
    extends AsyncHttpClientHandler[Task, Nothing](asyncHttpClient,
                                                  TaskMonad,
                                                  closeClient) {

  override protected def streamBodyToPublisher(
      s: Nothing): Publisher[ByteBuffer] = s // nothing is everything

  override protected def publisherToStreamBody(
      p: Publisher[ByteBuffer]): Nothing =
    throw new IllegalStateException("This handler does not support streaming")

  override protected def publisherToString(
      p: Publisher[ByteBuffer]): Task[String] =
    throw new IllegalStateException("This handler does not support streaming")
}

object AsyncHttpClientScalazHandler {
  def apply(): SttpHandler[Task, Nothing] =
    new AsyncHttpClientScalazHandler(new DefaultAsyncHttpClient(),
                                     closeClient = true)
  def usingConfig(cfg: AsyncHttpClientConfig): SttpHandler[Task, Nothing] =
    new AsyncHttpClientScalazHandler(new DefaultAsyncHttpClient(cfg),
                                     closeClient = true)
  def usingClient(client: AsyncHttpClient): SttpHandler[Task, Nothing] =
    new AsyncHttpClientScalazHandler(client, closeClient = false)
}

private[scalaz] object TaskMonad extends MonadAsyncError[Task] {
  override def unit[T](t: T): Task[T] = Task.point(t)

  override def map[T, T2](fa: Task[T])(f: (T) => T2): Task[T2] = fa.map(f)

  override def flatMap[T, T2](fa: Task[T])(f: (T) => Task[T2]): Task[T2] =
    fa.flatMap(f)

  override def async[T](
      register: ((Either[Throwable, T]) => Unit) => Unit): Task[T] =
    Task.async { cb =>
      register {
        case Left(t)  => cb(-\/(t))
        case Right(t) => cb(\/-(t))
      }
    }

  override def error[T](t: Throwable): Task[T] = Task.fail(t)
}
