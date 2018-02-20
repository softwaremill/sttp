package com.softwaremill.sttp.okhttp.monix

import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue

import com.softwaremill.sttp.{SttpBackend, _}
import com.softwaremill.sttp.okhttp.{OkHttpAsyncBackend, OkHttpBackend}
import monix.eval.Task
import monix.execution.Ack.Continue
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import okhttp3.{MediaType, OkHttpClient, RequestBody => OkHttpRequestBody}
import okio.BufferedSink

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class OkHttpMonixBackend private (client: OkHttpClient, closeClient: Boolean)(implicit s: Scheduler)
    extends OkHttpAsyncBackend[Task, Observable[ByteBuffer]](client, TaskMonad, closeClient) {

  override def streamToRequestBody(stream: Observable[ByteBuffer]): Option[OkHttpRequestBody] =
    Some(new OkHttpRequestBody() {
      override def writeTo(sink: BufferedSink): Unit =
        toIterable(stream) map (_.array()) foreach sink.write
      override def contentType(): MediaType = null
    })

  override def responseBodyToStream(res: okhttp3.Response): Try[Observable[ByteBuffer]] =
    Success(
      Observable
        .fromInputStream(res.body().byteStream())
        .map(ByteBuffer.wrap)
        .doAfterTerminate(_ => res.close()))

  private def toIterable[T](observable: Observable[T])(implicit s: Scheduler): Iterable[T] =
    new Iterable[T] {
      override def iterator: Iterator[T] = new Iterator[T] {
        case object Completed extends Exception

        val blockingQueue = new ArrayBlockingQueue[Either[Throwable, T]](1)

        observable.executeWithFork.subscribe(new Subscriber[T] {
          override implicit def scheduler: Scheduler = s

          override def onError(ex: Throwable): Unit = {
            blockingQueue.put(Left(ex))
          }

          override def onComplete(): Unit = {
            blockingQueue.put(Left(Completed))
          }

          override def onNext(elem: T): Future[Ack] = {
            blockingQueue.put(Right(elem))
            Continue
          }
        })

        var value: T = _

        override def hasNext: Boolean =
          blockingQueue.take() match {
            case Left(Completed) => false
            case Right(elem) =>
              value = elem
              true
            case Left(ex) => throw ex
          }

        override def next(): T = value
      }
    }
}

object OkHttpMonixBackend {
  private def apply(client: OkHttpClient, closeClient: Boolean)(
      implicit s: Scheduler): SttpBackend[Task, Observable[ByteBuffer]] =
    new FollowRedirectsBackend(new OkHttpMonixBackend(client, closeClient)(s))

  def apply(options: SttpBackendOptions = SttpBackendOptions.Default)(
      implicit s: Scheduler = Scheduler.Implicits.global): SttpBackend[Task, Observable[ByteBuffer]] =
    OkHttpMonixBackend(OkHttpBackend.defaultClient(DefaultReadTimeout.toMillis, options), closeClient = true)(s)

  def usingClient(client: OkHttpClient)(
      implicit s: Scheduler = Scheduler.Implicits.global): SttpBackend[Task, Observable[ByteBuffer]] =
    OkHttpMonixBackend(client, closeClient = false)(s)
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
    rt.onErrorRecoverWith {
      case t => h(t)
    }
}
