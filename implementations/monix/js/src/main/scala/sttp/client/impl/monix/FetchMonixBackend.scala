package sttp.client.impl.monix

import java.nio.ByteBuffer

import monix.eval.Task
import monix.reactive.Observable
import org.scalajs.dom.experimental.{BodyInit, Response => FetchResponse}
import sttp.client.{AbstractFetchBackend, FetchOptions, NothingT, ResponseAsStream, SttpBackend}

import scala.scalajs.js
import scala.scalajs.js.Promise
import scala.scalajs.js.typedarray.{Int8Array, _}
import org.scalajs.dom.experimental.{Request => FetchRequest}

/**
  * Uses the `ReadableStream` interface from the Streams API.
  *
  * Streams are behind a flag on Firefox.
  *
  * Note that no browsers support a stream request body so it is converted
  * into an in memory array first.
  *
  * @see https://developer.mozilla.org/en-US/docs/Web/API/ReadableStream
  */
class FetchMonixBackend private (fetchOptions: FetchOptions, customizeRequest: FetchRequest => FetchRequest)
    extends AbstractFetchBackend[Task, Observable[ByteBuffer]](fetchOptions, customizeRequest)(TaskMonadAsyncError) {

  override protected def addCancelTimeoutHook[T](result: Task[T], cancel: () => Unit): Task[T] = {
    val doCancel = Task.delay(cancel())
    result.doOnCancel(doCancel).doOnFinish(_ => doCancel)
  }

  override protected def handleStreamBody(s: Observable[ByteBuffer]): Task[js.UndefOr[BodyInit]] = {
    // as no browsers support a ReadableStream request body yet we need to create an in memory array
    // see: https://stackoverflow.com/a/41222366/4094860
    val bytes = s.foldLeftL(Array.emptyByteArray) { case (data, item) => data ++ item.array() }
    bytes.map(_.toTypedArray.asInstanceOf[BodyInit])
  }

  override protected def handleResponseAsStream[T](
      ras: ResponseAsStream[T, Observable[ByteBuffer]],
      response: FetchResponse
  ): Task[T] = {
    Task
      .delay {

        lazy val reader = response.body.getReader()

        def read() = transformPromise(reader.read())

        def go(): Observable[ByteBuffer] = {
          Observable.fromTask(read()).flatMap { chunk =>
            if (chunk.done) Observable.empty
            else {
              val bytes = new Int8Array(chunk.value.buffer).toArray
              Observable.pure(ByteBuffer.wrap(bytes)) ++ go()
            }
          }
        }
        go().doOnSubscriptionCancel(Task(reader.cancel("Response body reader cancelled")).void)
      }
      .map(ras.responseIsStream)
  }

  override protected def transformPromise[T](promise: => Promise[T]): Task[T] = Task.fromFuture(promise.toFuture)

}

object FetchMonixBackend {

  def apply(
      fetchOptions: FetchOptions = FetchOptions.Default,
      customizeRequest: FetchRequest => FetchRequest = identity
  ): SttpBackend[Task, Observable[ByteBuffer], NothingT] =
    new FetchMonixBackend(fetchOptions, customizeRequest)
}
