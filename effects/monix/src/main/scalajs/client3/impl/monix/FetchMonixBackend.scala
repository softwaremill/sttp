package client3.impl.monix

import monix.eval.Task
import monix.reactive.Observable
import org.scalajs.dom.experimental.{BodyInit, Request => FetchRequest, Response => FetchResponse}
import sttp.capabilities.monix.MonixStreams
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{AbstractFetchBackend, ConvertFromFuture, FetchOptions, SttpBackend}

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.Promise
import scala.scalajs.js.typedarray.{Int8Array, _}

/** Uses the `ReadableStream` interface from the Streams API.
  *
  * Streams are behind a flag on Firefox.
  *
  * Note that no browsers support a stream request body so it is converted
  * into an in memory array first.
  *
  * @see https://developer.mozilla.org/en-US/docs/Web/API/ReadableStream
  */
class FetchMonixBackend private (fetchOptions: FetchOptions, customizeRequest: FetchRequest => FetchRequest)
    extends AbstractFetchBackend[Task, MonixStreams, MonixStreams](
      fetchOptions,
      customizeRequest,
      FetchMonixBackend.convertFromFuture
    )(
      TaskMonadAsyncError
    ) {

  override val streams: MonixStreams = MonixStreams

  override protected def addCancelTimeoutHook[T](result: Task[T], cancel: () => Unit): Task[T] = {
    val doCancel = Task.delay(cancel())
    result.doOnCancel(doCancel).doOnFinish(_ => doCancel)
  }

  override protected def handleStreamBody(s: Observable[Array[Byte]]): Task[js.UndefOr[BodyInit]] = {
    // as no browsers support a ReadableStream request body yet we need to create an in memory array
    // see: https://stackoverflow.com/a/41222366/4094860
    val bytes = s.foldLeftL(Array.emptyByteArray) { case (data, item) => data ++ item }
    bytes.map(_.toTypedArray.asInstanceOf[BodyInit])
  }

  override protected def handleResponseAsStream(
      response: FetchResponse
  ): Task[(Observable[Array[Byte]], () => Task[Unit])] = {
    Task
      .delay {
        lazy val reader = response.body.getReader()

        def read() = transformPromise(reader.read())

        def go(): Observable[Array[Byte]] = {
          Observable.fromTask(read()).flatMap { chunk =>
            if (chunk.done) Observable.empty
            else {
              val bytes = new Int8Array(chunk.value.buffer).toArray
              Observable.pure(bytes) ++ go()
            }
          }
        }
        val cancel = Task(reader.cancel("Response body reader cancelled")).void
        (go().doOnSubscriptionCancel(cancel), () => cancel)
      }
  }

  override protected def transformPromise[T](promise: => Promise[T]): Task[T] = Task.fromFuture(promise.toFuture)
}

object FetchMonixBackend {
  def apply(
      fetchOptions: FetchOptions = FetchOptions.Default,
      customizeRequest: FetchRequest => FetchRequest = identity
  ): SttpBackend[Task, MonixStreams] =
    new FetchMonixBackend(fetchOptions, customizeRequest)

  private lazy val convertFromFuture = new ConvertFromFuture[Task] {
    override def fromFuture[T](f: Future[T]): Task[T] = Task.fromFuture(f)
  }

  /** Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports `Observable[ByteBuffer]`
    * streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[Task, MonixStreams] = SttpBackendStub(TaskMonadAsyncError)
}
