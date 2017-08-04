package com.softwaremill.sttp.okhttp.monix

import java.nio.ByteBuffer

import com.softwaremill.sttp._
import com.softwaremill.sttp.okhttp.OkHttpAsyncClientHandler
import monix.eval.{Callback, Task}
import monix.execution.cancelables.AssignableCancelable
import monix.execution.{Cancelable, Scheduler}
import monix.reactive.observers.Subscriber
import monix.reactive.{Consumer, Observable}
import okhttp3.{MediaType, OkHttpClient, RequestBody => OkHttpRequestBody}
import okio.{BufferedSink, Okio}

import scala.language.higherKinds
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
        stream
          .consumeWith(
            Consumer.foreach(chunck => sink.write(chunck.array()))
          )
          .runAsync

      override def contentType(): MediaType = null
    })

  override def responseBodyToStream(
      res: okhttp3.Response): Try[Observable[ByteBuffer]] =
    Success(
      Observable.fromInputStream(res.body().byteStream()).map(ByteBuffer.wrap))
}

object OkHttpMonixClientHandler {
  def apply(okhttpClient: OkHttpClient = new OkHttpClient())(
      implicit s: Scheduler = Scheduler.Implicits.global)
    : OkHttpMonixClientHandler =
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
