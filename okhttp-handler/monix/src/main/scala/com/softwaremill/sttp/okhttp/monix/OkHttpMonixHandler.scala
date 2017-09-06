package com.softwaremill.sttp.okhttp.monix

import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue

import com.softwaremill.sttp.{SttpHandler, _}
import com.softwaremill.sttp.okhttp.{OkHttpAsyncHandler, OkHttpHandler}
import monix.eval.Task
import monix.execution.Ack.Continue
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import okhttp3.{MediaType, OkHttpClient, RequestBody => OkHttpRequestBody}
import okio.BufferedSink

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

class OkHttpMonixHandler private (client: OkHttpClient, closeClient: Boolean)(
    implicit s: Scheduler)
    extends OkHttpAsyncHandler[Task, Observable[ByteBuffer]](client,
                                                             TaskMonad,
                                                             closeClient) {

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
      Observable
        .fromInputStream(res.body().byteStream())
        .map(ByteBuffer.wrap)
        .doAfterTerminate(_ => res.close()))

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

object OkHttpMonixHandler {
  private def apply(client: OkHttpClient, closeClient: Boolean)(
      implicit s: Scheduler): SttpHandler[Task, Observable[ByteBuffer]] =
    new FollowRedirectsHandler(new OkHttpMonixHandler(client, closeClient)(s))

  def apply(connectionTimeout: FiniteDuration = SttpHandler.DefaultConnectionTimeout)(
      implicit s: Scheduler = Scheduler.Implicits.global)
    : SttpHandler[Task, Observable[ByteBuffer]] =
    OkHttpMonixHandler(
      OkHttpHandler.defaultClient(DefaultReadTimeout.toMillis, connectionTimeout.toMillis),
      closeClient = true)(s)

  def usingClient(client: OkHttpClient)(implicit s: Scheduler =
                                          Scheduler.Implicits.global)
    : SttpHandler[Task, Observable[ByteBuffer]] =
    OkHttpMonixHandler(client, closeClient = false)(s)
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
