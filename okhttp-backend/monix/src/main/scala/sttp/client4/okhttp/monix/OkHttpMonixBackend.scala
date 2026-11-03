package sttp.client4.okhttp.monix

import java.io.InputStream
import java.io.IOException
import cats.effect.Resource
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import okhttp3.{MediaType, OkHttpClient, RequestBody => OkHttpRequestBody}
import okio.BufferedSink
import sttp.capabilities.WebSockets
import sttp.capabilities.monix.MonixStreams
import sttp.client4.impl.monix.{MonixSimpleQueue, MonixWebSockets, TaskMonadAsyncError}
import sttp.client4.internal.ws.SimpleQueue
import sttp.monad.MonadError
import sttp.client4.okhttp.{BodyFromOkHttp, BodyToOkHttp, CancellableRequestBody, OkHttpAsyncBackend, OkHttpBackend}
import sttp.client4.testing.WebSocketStreamBackendStub
import sttp.client4._
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.ws.{WebSocket, WebSocketFrame}

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration.Duration
import sttp.client4.compression.CompressionHandlers
import sttp.client4.compression.Compressor
import sttp.client4.compression.Decompressor
import cats.effect.ExitCase

class OkHttpMonixBackend private (
    client: OkHttpClient,
    closeClient: Boolean,
    compressionHandlers: CompressionHandlers[MonixStreams, InputStream],
    webSocketBufferCapacity: Option[Int]
)(implicit
    s: Scheduler
) extends OkHttpAsyncBackend[Task, MonixStreams, MonixStreams & WebSockets](
      client,
      TaskMonadAsyncError,
      closeClient,
      compressionHandlers
    )
    with WebSocketStreamBackend[Task, MonixStreams] {
  override val streams: MonixStreams = MonixStreams

  // writing the request body blocks on socket I/O, so it shouldn't run on the default (compute) scheduler
  private val requestBodyScheduler = Scheduler.io("sttp-okhttp-monix-request-body")

  override def close(): Task[Unit] = super.close().guarantee(Task.eval(requestBodyScheduler.shutdown()))

  override protected val bodyToOkHttp: BodyToOkHttp[Task, MonixStreams] = new BodyToOkHttp[Task, MonixStreams] {
    override val streams: MonixStreams = MonixStreams

    override def streamToRequestBody(
        stream: streams.BinaryStream,
        mt: MediaType,
        cl: Option[Long]
    ): OkHttpRequestBody =
      new OkHttpRequestBody() with CancellableRequestBody {
        private val cancelled = Promise[Unit]()

        override def writeTo(sink: BufferedSink): Unit = {
          val done = Promise[Unit]()
          val running = stream
            .foreachL(chunk => { sink.write(chunk); () })
            .executeOn(requestBodyScheduler)
            .runAsync { r =>
              val _ = done.tryComplete(r.toTry)
            }
          cancelled.future.foreach { _ =>
            running.cancel()
            val _ = done.tryFailure(new IOException("Request cancelled"))
          }
          try Await.result(done.future, Duration.Inf)
          catch {
            case e: Throwable =>
              running.cancel()
              throw e
          }
        }

        override def cancel(): Unit = { val _ = cancelled.trySuccess(()) }
        override def contentType(): MediaType = mt
        override def contentLength(): Long = cl.getOrElse(super.contentLength())
      }
  }

  override protected val bodyFromOkHttp: BodyFromOkHttp[Task, MonixStreams] = new BodyFromOkHttp[Task, MonixStreams] {
    override val streams: MonixStreams = MonixStreams
    override implicit val monad: MonadError[Task] = OkHttpMonixBackend.this.monad

    override def responseBodyToStream(inputStream: InputStream): Observable[Array[Byte]] =
      Observable
        .fromInputStream(Task.now(inputStream))
        .guarantee(Task.eval(inputStream.close()))

    override def compileWebSocketPipe(
        ws: WebSocket[Task],
        pipe: streams.Pipe[WebSocketFrame.Data[?], WebSocketFrame]
    ): Task[Unit] = MonixWebSockets.compilePipe(ws, pipe)
  }

  override protected def createSimpleQueue[T]: Task[SimpleQueue[Task, T]] =
    Task.eval(new MonixSimpleQueue[T](webSocketBufferCapacity))

  override protected def ensureOnAbnormal[T](effect: Task[T])(finalizer: => Task[Unit]): Task[T] =
    effect.guaranteeCase { exit =>
      if (exit == ExitCase.Completed) Task.unit else finalizer.onErrorHandleWith(t => Task.eval(t.printStackTrace()))
    }
}

object OkHttpMonixBackend {
  val DefaultCompressionHandlers: CompressionHandlers[Any, InputStream] =
    CompressionHandlers(Compressor.default[Any], Decompressor.defaultInputStream)

  private def apply(
      client: OkHttpClient,
      closeClient: Boolean,
      compressionHandlers: CompressionHandlers[Any, InputStream],
      webSocketBufferCapacity: Option[Int]
  )(implicit
      s: Scheduler
  ): WebSocketStreamBackend[Task, MonixStreams] =
    FollowRedirectsBackend(
      new OkHttpMonixBackend(client, closeClient, compressionHandlers, webSocketBufferCapacity)(using s)
    )

  def apply(
      options: BackendOptions = BackendOptions.Default,
      compressionHandlers: CompressionHandlers[Any, InputStream] = DefaultCompressionHandlers,
      webSocketBufferCapacity: Option[Int] = OkHttpBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Task[WebSocketStreamBackend[Task, MonixStreams]] =
    Task.eval(
      OkHttpMonixBackend(
        OkHttpBackend.defaultClient(DefaultReadTimeout.toMillis, options),
        closeClient = true,
        compressionHandlers,
        webSocketBufferCapacity
      )(using s)
    )

  def resource(
      options: BackendOptions = BackendOptions.Default,
      compressionHandlers: CompressionHandlers[Any, InputStream] = DefaultCompressionHandlers,
      webSocketBufferCapacity: Option[Int] = OkHttpBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, WebSocketStreamBackend[Task, MonixStreams]] =
    Resource.make(apply(options, compressionHandlers, webSocketBufferCapacity))(_.close())

  /** Creates a backend using the given client. The client is closed when the resource is released. */
  def resourceUsingClient(
      client: OkHttpClient,
      compressionHandlers: CompressionHandlers[Any, InputStream] = DefaultCompressionHandlers,
      webSocketBufferCapacity: Option[Int] = OkHttpBackend.DefaultWebSocketBufferCapacity
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, WebSocketStreamBackend[Task, MonixStreams]] =
    Resource.make(
      Task.eval(OkHttpMonixBackend(client, closeClient = true, compressionHandlers, webSocketBufferCapacity)(using s))
    )(_.close())

  def usingClient(
      client: OkHttpClient,
      compressionHandlers: CompressionHandlers[Any, InputStream] = DefaultCompressionHandlers,
      webSocketBufferCapacity: Option[Int] = OkHttpBackend.DefaultWebSocketBufferCapacity
  )(implicit s: Scheduler = Scheduler.global): WebSocketStreamBackend[Task, MonixStreams] =
    OkHttpMonixBackend(client, closeClient = false, compressionHandlers, webSocketBufferCapacity)(using s)

  /** Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports `Observable[ByteBuffer]`
    * streaming.
    *
    * See [[WebSocketStreamBackendStub]] for details on how to configure stub responses.
    */
  def stub: WebSocketStreamBackendStub[Task, MonixStreams] = WebSocketStreamBackendStub(TaskMonadAsyncError)
}
