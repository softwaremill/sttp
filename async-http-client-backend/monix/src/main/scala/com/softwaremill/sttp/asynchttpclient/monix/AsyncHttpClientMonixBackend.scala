package com.softwaremill.sttp.asynchttpclient.monix

import java.nio.ByteBuffer

import com.softwaremill.sttp.asynchttpclient.AsyncHttpClientBackend
import com.softwaremill.sttp.{FollowRedirectsBackend, MonadAsyncError, SttpBackend, SttpBackendOptions, Utf8, concatByteBuffers}
import monix.eval.Task
import monix.execution.{Cancelable, Scheduler}
import monix.reactive.Observable
import org.asynchttpclient.{AsyncHttpClient, AsyncHttpClientConfig, DefaultAsyncHttpClient}
import org.reactivestreams.Publisher

import scala.util.{Failure, Success}

class AsyncHttpClientMonixBackend private (asyncHttpClient: AsyncHttpClient, closeClient: Boolean)(
    implicit scheduler: Scheduler)
    extends AsyncHttpClientBackend[Task, Observable[ByteBuffer]](asyncHttpClient, TaskMonad, closeClient) {

  override protected def streamBodyToPublisher(s: Observable[ByteBuffer]): Publisher[ByteBuffer] =
    s.toReactivePublisher

  override protected def publisherToStreamBody(p: Publisher[ByteBuffer]): Observable[ByteBuffer] =
    Observable.fromReactivePublisher(p)

  override protected def publisherToString(p: Publisher[ByteBuffer]): Task[String] = {

    val bytes = Observable
      .fromReactivePublisher(p)
      .foldLeftL(ByteBuffer.allocate(0))(concatByteBuffers)

    bytes.map(bb => new String(bb.array(), Utf8))
  }
}

object AsyncHttpClientMonixBackend {

  private def apply(asyncHttpClient: AsyncHttpClient, closeClient: Boolean)(
      implicit scheduler: Scheduler): SttpBackend[Task, Observable[ByteBuffer]] =
    new FollowRedirectsBackend(new AsyncHttpClientMonixBackend(asyncHttpClient, closeClient))

  /**
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
  def apply(options: SttpBackendOptions = SttpBackendOptions.Default)(
      implicit s: Scheduler = Scheduler.Implicits.global): SttpBackend[Task, Observable[ByteBuffer]] =
    AsyncHttpClientMonixBackend(AsyncHttpClientBackend.defaultClient(options), closeClient = true)

  /**
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
  def usingConfig(cfg: AsyncHttpClientConfig)(
      implicit s: Scheduler = Scheduler.Implicits.global): SttpBackend[Task, Observable[ByteBuffer]] =
    AsyncHttpClientMonixBackend(new DefaultAsyncHttpClient(cfg), closeClient = true)

  /**
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
  def usingClient(client: AsyncHttpClient)(
      implicit s: Scheduler = Scheduler.Implicits.global): SttpBackend[Task, Observable[ByteBuffer]] =
    AsyncHttpClientMonixBackend(client, closeClient = false)
}

private[monix] object TaskMonad extends MonadAsyncError[Task] {
  override def unit[T](t: T): Task[T] = Task.now(t)

  override def map[T, T2](fa: Task[T])(f: (T) => T2): Task[T2] = fa.map(f)

  override def flatMap[T, T2](fa: Task[T])(f: (T) => Task[T2]): Task[T2] =
    fa.flatMap(f)

  override def async[T](register: ((Either[Throwable, T]) => Unit) => Unit): Task[T] =
    Task.async { (_, cb) =>
      register {
        case Left(t)  => cb(Failure(t))
        case Right(t) => cb(Success(t))
      }

      Cancelable.empty
    }

  override def error[T](t: Throwable): Task[T] = Task.raiseError(t)

  override protected def handleWrappedError[T](rt: Task[T])(h: PartialFunction[Throwable, Task[T]]): Task[T] =
    rt.onErrorRecoverWith(h)
}
