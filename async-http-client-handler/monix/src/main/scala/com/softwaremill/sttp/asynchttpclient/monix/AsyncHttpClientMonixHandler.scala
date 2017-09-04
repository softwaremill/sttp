package com.softwaremill.sttp.asynchttpclient.monix

import java.nio.ByteBuffer

import com.softwaremill.sttp.{
  FollowRedirectsHandler,
  MonadAsyncError,
  SttpHandler,
  Utf8,
  concatByteBuffers
}
import com.softwaremill.sttp.asynchttpclient.AsyncHttpClientHandler
import monix.eval.Task
import monix.execution.{Cancelable, Scheduler}
import monix.reactive.Observable
import org.asynchttpclient.{
  AsyncHttpClient,
  AsyncHttpClientConfig,
  DefaultAsyncHttpClient
}
import org.reactivestreams.Publisher

import scala.util.{Failure, Success}

class AsyncHttpClientMonixHandler private (
    asyncHttpClient: AsyncHttpClient,
    closeClient: Boolean)(implicit scheduler: Scheduler)
    extends AsyncHttpClientHandler[Task, Observable[ByteBuffer]](
      asyncHttpClient,
      TaskMonad,
      closeClient) {

  override protected def streamBodyToPublisher(
      s: Observable[ByteBuffer]): Publisher[ByteBuffer] =
    s.toReactivePublisher

  override protected def publisherToStreamBody(
      p: Publisher[ByteBuffer]): Observable[ByteBuffer] =
    Observable.fromReactivePublisher(p)

  override protected def publisherToString(
      p: Publisher[ByteBuffer]): Task[String] = {

    val bytes = Observable
      .fromReactivePublisher(p)
      .foldLeftL(ByteBuffer.allocate(0))(concatByteBuffers)

    bytes.map(bb => new String(bb.array(), Utf8))
  }
}

object AsyncHttpClientMonixHandler {

  private def apply(asyncHttpClient: AsyncHttpClient, closeClient: Boolean)(
      implicit scheduler: Scheduler)
    : SttpHandler[Task, Observable[ByteBuffer]] =
    new FollowRedirectsHandler(
      new AsyncHttpClientMonixHandler(asyncHttpClient, closeClient))

  /**
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
  def apply()(implicit s: Scheduler = Scheduler.Implicits.global)
    : SttpHandler[Task, Observable[ByteBuffer]] =
    AsyncHttpClientMonixHandler(new DefaultAsyncHttpClient(),
                                closeClient = true)

  /**
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
  def usingConfig(cfg: AsyncHttpClientConfig)(implicit s: Scheduler =
                                                Scheduler.Implicits.global)
    : SttpHandler[Task, Observable[ByteBuffer]] =
    AsyncHttpClientMonixHandler(new DefaultAsyncHttpClient(cfg),
                                closeClient = true)

  /**
    * @param s The scheduler used for streaming request bodies. Defaults to the
    *          global scheduler.
    */
  def usingClient(client: AsyncHttpClient)(implicit s: Scheduler =
                                             Scheduler.Implicits.global)
    : SttpHandler[Task, Observable[ByteBuffer]] =
    AsyncHttpClientMonixHandler(client, closeClient = false)
}

private[monix] object TaskMonad extends MonadAsyncError[Task] {
  override def unit[T](t: T): Task[T] = Task.now(t)

  override def map[T, T2](fa: Task[T])(f: (T) => T2): Task[T2] = fa.map(f)

  override def flatMap[T, T2](fa: Task[T])(f: (T) => Task[T2]): Task[T2] =
    fa.flatMap(f)

  override def async[T](
      register: ((Either[Throwable, T]) => Unit) => Unit): Task[T] =
    Task.async { (_, cb) =>
      register {
        case Left(t)  => cb(Failure(t))
        case Right(t) => cb(Success(t))
      }

      Cancelable.empty
    }

  override def error[T](t: Throwable): Task[T] = Task.raiseError(t)
}
