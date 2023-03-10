package sttp.client4.okhttp.monix

import java.io.InputStream
import java.util.concurrent.ArrayBlockingQueue

import cats.effect.Resource
import monix.eval.Task
import monix.execution.Ack.Continue
import monix.execution.{Ack, Scheduler}
import monix.reactive.Observable
import monix.reactive.observers.Subscriber
import okhttp3.{MediaType, OkHttpClient, RequestBody => OkHttpRequestBody}
import okio.BufferedSink
import sttp.capabilities.WebSockets
import sttp.capabilities.monix.MonixStreams
import sttp.client4.impl.monix.{MonixSimpleQueue, MonixWebSockets, TaskMonadAsyncError}
import sttp.client4.internal.ws.SimpleQueue
import sttp.monad.MonadError
import sttp.client4.okhttp.OkHttpBackend.EncodingHandler
import sttp.client4.okhttp.{BodyFromOkHttp, BodyToOkHttp, OkHttpAsyncBackend, OkHttpBackend}
import sttp.client4.testing.WebSocketStreamBackendStub
import sttp.client4._
import sttp.ws.{WebSocket, WebSocketFrame}

import scala.concurrent.Future

class OkHttpMonixBackend private (
    client: OkHttpClient,
    closeClient: Boolean,
    customEncodingHandler: EncodingHandler,
    webSocketBufferCapacity: Option[Int]
)(implicit
    s: Scheduler
) extends OkHttpAsyncBackend[Task, MonixStreams, MonixStreams with WebSockets](
      client,
      TaskMonadAsyncError,
      closeClient,
      customEncodingHandler
    )
    with WebSocketStreamBackend[Task, MonixStreams] {
  override val streams: MonixStreams = MonixStreams

  override protected val bodyToOkHttp: BodyToOkHttp[Task, MonixStreams] = new BodyToOkHttp[Task, MonixStreams] {
    override val streams: MonixStreams = MonixStreams

    override def streamToRequestBody(
        stream: streams.BinaryStream,
        mt: MediaType,
        cl: Option[Long]
    ): OkHttpRequestBody = {
      new OkHttpRequestBody() {
        override def writeTo(sink: BufferedSink): Unit = toIterable(stream).foreach(sink.write)
        override def contentType(): MediaType = mt
        override def contentLength(): Long = cl.getOrElse(super.contentLength())
      }
    }
  }

  override protected val bodyFromOkHttp: BodyFromOkHttp[Task, MonixStreams] = new BodyFromOkHttp[Task, MonixStreams] {
    override val streams: MonixStreams = MonixStreams
    override implicit val monad: MonadError[Task] = OkHttpMonixBackend.this.responseMonad

    override def responseBodyToStream(inputStream: InputStream): Observable[Array[Byte]] =
      Observable
        .fromInputStream(Task.now(inputStream))
        .guarantee(Task.eval(inputStream.close()))

    override def compileWebSocketPipe(
        ws: WebSocket[Task],
        pipe: streams.Pipe[WebSocketFrame.Data[_], WebSocketFrame]
    ): Task[Unit] = MonixWebSockets.compilePipe(ws, pipe)
  }

  private def toIterable[T](observable: Observable[T])(implicit s: Scheduler): Iterable[T] =
    new Iterable[T] {
      override def iterator: Iterator[T] =
        new Iterator[T] {
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

  override protected def createSimpleQueue[T]: Task[SimpleQueue[Task, T]] =
    Task.eval(new MonixSimpleQueue[T](webSocketBufferCapacity))
}

object OkHttpMonixBackend {
  private def apply(
      client: OkHttpClient,
      closeClient: Boolean,
      customEncodingHandler: EncodingHandler,
      webSocketBufferCapacity: Option[Int]
  )(implicit
      s: Scheduler
  ): WebSocketStreamBackend[Task, MonixStreams] =
    FollowRedirectsBackend(
      new OkHttpMonixBackend(client, closeClient, customEncodingHandler, webSocketBufferCapacity)(s)
    )

  def apply(
             options: BackendOptions = BackendOptions.Default,
             customEncodingHandler: EncodingHandler = PartialFunction.empty,
             webSocketBufferCapacity: Option[Int] = OkHttpBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Task[WebSocketStreamBackend[Task, MonixStreams]] =
    Task.eval(
      OkHttpMonixBackend(
        OkHttpBackend.defaultClient(DefaultReadTimeout.toMillis, options),
        closeClient = true,
        customEncodingHandler,
        webSocketBufferCapacity
      )(s)
    )

  def resource(
                options: BackendOptions = BackendOptions.Default,
                customEncodingHandler: EncodingHandler = PartialFunction.empty,
                webSocketBufferCapacity: Option[Int] = OkHttpBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, WebSocketStreamBackend[Task, MonixStreams]] =
    Resource.make(apply(options, customEncodingHandler, webSocketBufferCapacity))(_.close())

  def usingClient(
      client: OkHttpClient,
      customEncodingHandler: EncodingHandler = PartialFunction.empty,
      webSocketBufferCapacity: Option[Int] = OkHttpBackend.DefaultWebSocketBufferCapacity
  )(implicit s: Scheduler = Scheduler.global): WebSocketStreamBackend[Task, MonixStreams] =
    OkHttpMonixBackend(client, closeClient = false, customEncodingHandler, webSocketBufferCapacity)(s)

  /** Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports `Observable[ByteBuffer]`
    * streaming.
    *
    * See [[WebSocketStreamBackendStub]] for details on how to configure stub responses.
    */
  def stub: WebSocketStreamBackendStub[Task, MonixStreams] = WebSocketStreamBackendStub(TaskMonadAsyncError)
}
