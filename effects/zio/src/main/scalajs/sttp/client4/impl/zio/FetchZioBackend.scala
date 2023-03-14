package sttp.client4.impl.zio

import zio._
import zio.stream._

import org.scalajs.dom.experimental.{BodyInit, Request => FetchRequest, Response => FetchResponse}
import sttp.capabilities.zio.ZioStreams
import sttp.client4.internal.ConvertFromFuture
import sttp.client4.testing.WebSocketStreamBackendStub
import sttp.client4.WebSocketStreamBackend
import sttp.client4.fetch.{AbstractFetchBackend, FetchOptions}
import sttp.ws.{WebSocket, WebSocketFrame}

import scala.concurrent.Future
import scala.scalajs.js
import scala.scalajs.js.typedarray.{Int8Array, _}

/** Uses the `ReadableStream` interface from the Streams API.
  *
  * Streams are behind a flag on Firefox.
  *
  * Note that no browsers support a stream request body so it is converted into an in memory array first.
  *
  * @see
  *   https://developer.mozilla.org/en-US/docs/Web/API/ReadableStream
  */
class FetchZioBackend private (fetchOptions: FetchOptions, customizeRequest: FetchRequest => FetchRequest)
    extends AbstractFetchBackend[Task, ZioStreams](fetchOptions, customizeRequest, new RIOMonadAsyncError[Any])
    with WebSocketStreamBackend[Task, ZioStreams] {

  type Observable[+A] = ZStream[Any, Throwable, A]

  override val streams: ZioStreams = ZioStreams

  override protected def addCancelTimeoutHook[T](result: Task[T], cancel: () => Unit): Task[T] = {
    val doCancel = ZIO.attempt(cancel())
    result.onInterrupt(doCancel.catchAll(_ => ZIO.unit)).tap(_ => doCancel)
  }

  override protected def handleStreamBody(s: Observable[Byte]): Task[js.UndefOr[BodyInit]] = {
    // as no browsers support a ReadableStream request body yet we need to create an in memory array
    // see: https://stackoverflow.com/a/41222366/4094860
    val bytes = s.runCollect.map(_.toArray)
    bytes.map(_.toTypedArray.asInstanceOf[BodyInit])
  }

  override protected def handleResponseAsStream(
      response: FetchResponse
  ): Task[(Observable[Byte], () => Task[Unit])] =
    ZIO.attempt {
      lazy val reader = response.body.getReader()

      def read() = convertFromFuture(reader.read().toFuture)

      def go(): Observable[Byte] =
        ZStream.fromZIO(read()).flatMap { chunk =>
          if (chunk.done) ZStream.empty
          else {
            val bytes = new Int8Array(chunk.value.buffer).toArray
            ZStream.fromChunk(Chunk.fromArray(bytes)) ++ go()
          }
        }
      val cancel = ZIO.fromPromiseJS(reader.cancel("Response body reader cancelled"))
      (ZStream.fromIterableZIO(go().runCollect.onInterrupt(cancel.catchAll(_ => ZIO.unit))), () => cancel)
    }

  override protected def compileWebSocketPipe(
      ws: WebSocket[Task],
      pipe: Observable[WebSocketFrame.Data[_]] => Observable[WebSocketFrame]
  ): Task[Unit] =
    ZioWebSockets.compilePipe(ws, pipe)

  override implicit def convertFromFuture: ConvertFromFuture[Task] = new ConvertFromFuture[Task] {
    override def apply[T](f: Future[T]): Task[T] = ZIO.fromFuture(implicit ec => f)
  }
}

object FetchZioBackend {
  def apply(
      fetchOptions: FetchOptions = FetchOptions.Default,
      customizeRequest: FetchRequest => FetchRequest = identity
  ): WebSocketStreamBackend[Task, ZioStreams] =
    new FetchZioBackend(fetchOptions, customizeRequest)

  /** Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports `Observable[ByteBuffer]`
    * streaming.
    *
    * See [[WebSocketStreamBackendStub]] for details on how to configure stub responses.
    */
  def stub: WebSocketStreamBackendStub[Task, ZioStreams] = WebSocketStreamBackendStub(new RIOMonadAsyncError[Any])
}
