package com.softwaremill.sttp.okhttp.monix

import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue

import com.softwaremill.sttp.{SttpHandler, _}
import com.softwaremill.sttp.okhttp.OkHttpAsyncClientHandler
import monix.eval.Task
import monix.execution.Ack.Continue
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import okhttp3.{MediaType, OkHttpClient, RequestBody => OkHttpRequestBody}
import okio.BufferedSink

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
  * Created by omainegra on 8/4/17.
  */
class OkHttpMonixClientHandler private (client: OkHttpClient)(
    implicit s: Scheduler)
    extends OkHttpAsyncClientHandler[Task, Observable[ByteBuffer]](client,
                                                                   TaskMonad) {

  override def streamToRequestBody(
      stream: Observable[ByteBuffer]): Option[OkHttpRequestBody] =
    Some(new OkHttpRequestBody() {
      override def writeTo(sink: BufferedSink): Unit =
        toIterable(stream) map (_.array()) foreach sink.write
      override def contentType(): MediaType = null
    })

  override def responseBodyToStream(
      res: okhttp3.Response): Try[Observable[ByteBuffer]] =
    Success(
      Observable.fromInputStream(res.body().byteStream()).map(ByteBuffer.wrap))

  private def toIterable[T](observable: Observable[T])(
      implicit s: Scheduler): Iterable[T] =
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

object OkHttpMonixClientHandler {
  def apply(okhttpClient: OkHttpClient = new OkHttpClient())(
      implicit s: Scheduler = Scheduler.Implicits.global)
    : SttpHandler[Task, Observable[ByteBuffer]] =
    new OkHttpMonixClientHandler(okhttpClient)(s)
}

private[monix] object TaskMonad extends MonadAsyncError[Task] {
  override def unit[T](t: T): Task[T] = Task.now(t)

  override def map[T, T2](fa: Task[T], f: (T) => T2): Task[T2] = fa.map(f)

  override def flatMap[T, T2](fa: Task[T], f: (T) => Task[T2]): Task[T2] =
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
