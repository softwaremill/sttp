package sttp.client.okhttp.monix

import java.nio.ByteBuffer
import java.util.concurrent.ArrayBlockingQueue

import monix.eval.Task
import monix.execution.Ack.Continue
import monix.execution.{Ack, Scheduler}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import okhttp3.{MediaType, OkHttpClient, RequestBody => OkHttpRequestBody}
import okio.BufferedSink
import sttp.client.impl.monix.TaskMonadAsyncError
import sttp.client.okhttp.{OkHttpAsyncBackend, OkHttpBackend}
import sttp.client.{SttpBackend, _}

import scala.concurrent.Future
import scala.util.{Success, Try}

class OkHttpMonixBackend private (client: OkHttpClient, closeClient: Boolean)(implicit s: Scheduler)
    extends OkHttpAsyncBackend[Task, Observable[ByteBuffer]](client, TaskMonadAsyncError, closeClient) {

  override def send[T](r: Request[T, Observable[ByteBuffer]]): Task[Response[T]] = {
    super.send(r).guarantee(Task.shift)
  }

  override def streamToRequestBody(stream: Observable[ByteBuffer]): Option[OkHttpRequestBody] =
    Some(new OkHttpRequestBody() {
      override def writeTo(sink: BufferedSink): Unit =
        toIterable(stream) map (_.array()) foreach sink.write
      override def contentType(): MediaType = null
    })

  override def responseBodyToStream(res: okhttp3.Response): Try[Observable[ByteBuffer]] =
    Success(
      Observable
        .fromInputStream(Task.now(res.body().byteStream()))
        .map(ByteBuffer.wrap)
        .guaranteeCase(_ => Task(res.close()))
    )

  private def toIterable[T](observable: Observable[T])(implicit s: Scheduler): Iterable[T] =
    new Iterable[T] {
      override def iterator: Iterator[T] = new Iterator[T] {
        case object Completed extends Exception

        val blockingQueue = new ArrayBlockingQueue[Either[Throwable, T]](1)

        observable.executeAsync.subscribe(new Subscriber[T] {
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
      implicit s: Scheduler
  ): SttpBackend[Task, Observable[ByteBuffer]] =
    new FollowRedirectsBackend(new OkHttpMonixBackend(client, closeClient)(s))

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default
  )(implicit s: Scheduler = Scheduler.Implicits.global): Task[SttpBackend[Task, Observable[ByteBuffer]]] =
    Task.eval(
      OkHttpMonixBackend(OkHttpBackend.defaultClient(DefaultReadTimeout.toMillis, options), closeClient = true)(s)
    )

  def usingClient(
      client: OkHttpClient
  )(implicit s: Scheduler = Scheduler.Implicits.global): SttpBackend[Task, Observable[ByteBuffer]] =
    OkHttpMonixBackend(client, closeClient = false)(s)
}
