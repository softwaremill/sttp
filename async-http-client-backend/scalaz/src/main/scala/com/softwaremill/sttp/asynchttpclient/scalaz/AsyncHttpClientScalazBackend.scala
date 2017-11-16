package com.softwaremill.sttp.asynchttpclient.scalaz

import java.nio.ByteBuffer

import com.softwaremill.sttp.{
  FollowRedirectsBackend,
  MonadAsyncError,
  SttpBackend,
  SttpBackendOptions
}
import com.softwaremill.sttp.asynchttpclient.AsyncHttpClientBackend
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  DefaultAsyncHttpClient
}
import org.reactivestreams.Publisher

import scalaz.{-\/, \/-}
import scalaz.concurrent.Task

class AsyncHttpClientScalazBackend private (asyncHttpClient: AsyncHttpClient,
                                            closeClient: Boolean)
    extends AsyncHttpClientBackend[Task, Nothing](asyncHttpClient,
                                                  TaskMonad,
                                                  closeClient) {

  override protected def streamBodyToPublisher(
      s: Nothing): Publisher[ByteBuffer] = s // nothing is everything

  override protected def publisherToStreamBody(
      p: Publisher[ByteBuffer]): Nothing =
    throw new IllegalStateException("This backend does not support streaming")

  override protected def publisherToString(
      p: Publisher[ByteBuffer]): Task[String] =
    throw new IllegalStateException("This backend does not support streaming")
}

object AsyncHttpClientScalazBackend {
  private def apply(asyncHttpClient: AsyncHttpClient,
                    closeClient: Boolean): SttpBackend[Task, Nothing] =
    new FollowRedirectsBackend[Task, Nothing](
      new AsyncHttpClientScalazBackend(asyncHttpClient, closeClient))

  def apply(options: SttpBackendOptions = SttpBackendOptions.Default)
    : SttpBackend[Task, Nothing] =
    AsyncHttpClientScalazBackend(AsyncHttpClientBackend.defaultClient(options),
                                 closeClient = true)

  def usingConfig(cfg: AsyncHttpClientConfig): SttpBackend[Task, Nothing] =
    AsyncHttpClientScalazBackend(new DefaultAsyncHttpClient(cfg),
                                 closeClient = true)

  def usingClient(client: AsyncHttpClient): SttpBackend[Task, Nothing] =
    AsyncHttpClientScalazBackend(client, closeClient = false)
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

  override protected def handleWrappedError[T](rt: Task[T])(
      h: PartialFunction[Throwable, Task[T]]): Task[T] = rt.handleWith(h)
}
