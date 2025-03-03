package sttp.client4.httpclient.zio

import _root_.zio.interop.reactivestreams._
import org.reactivestreams.FlowAdapters
import sttp.capabilities.zio.ZioStreams
import sttp.client4.BackendOptions
import sttp.client4.GenericRequest
import sttp.client4.Response
import sttp.client4.WebSocketStreamBackend
import sttp.client4.compression.CompressionHandlers
import sttp.client4.compression.Compressor
import sttp.client4.httpclient.HttpClientAsyncBackend
import sttp.client4.httpclient.HttpClientBackend
import sttp.client4.impl.zio.DeflateZioCompressor
import sttp.client4.impl.zio.DeflateZioDecompressor
import sttp.client4.impl.zio.GZipZioCompressor
import sttp.client4.impl.zio.GZipZioDecompressor
import sttp.client4.impl.zio.RIOMonadAsyncError
import sttp.client4.impl.zio.ZioSimpleQueue
import sttp.client4.internal._
import sttp.client4.internal.httpclient.BodyFromHttpClient
import sttp.client4.internal.httpclient.BodyToHttpClient
import sttp.client4.internal.httpclient.Sequencer
import sttp.client4.internal.httpclient.cancelPublisher
import sttp.client4.internal.httpclient.MultipartBodyBuilder
import sttp.client4.internal.httpclient.StreamMultipartBodyBuilder
import sttp.client4.internal.ws.SimpleQueue
import sttp.client4.testing.WebSocketStreamBackendStub
import sttp.client4.wrappers
import sttp.monad.MonadError
import zio._
import zio.Chunk.ByteArray
import zio.stream.ZStream

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublisher
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.nio.ByteBuffer
import java.io.File
import java.io.InputStream
import java.util
import java.{util => ju}
import java.util.concurrent.Flow.Publisher

class HttpClientZioBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    compressionHandlers: CompressionHandlers[ZioStreams, ZioStreams.BinaryStream]
) extends HttpClientAsyncBackend[
      Task,
      ZioStreams,
      Publisher[ju.List[ByteBuffer]],
      ZioStreams.BinaryStream
    ](
      client,
      new RIOMonadAsyncError[Any],
      closeClient,
      customizeRequest,
      compressionHandlers
    )
    with WebSocketStreamBackend[Task, ZioStreams] { self =>

  override val streams: ZioStreams = ZioStreams

  override protected def createBodyHandler: HttpResponse.BodyHandler[Publisher[util.List[ByteBuffer]]] =
    BodyHandlers.ofPublisher()

  override protected def emptyBody(): ZStream[Any, Throwable, Byte] = ZStream.empty

  override protected def lowLevelBodyToBody(p: Publisher[util.List[ByteBuffer]]): ZStream[Any, Throwable, Byte] =
    FlowAdapters.toPublisher(p).toZIOStream().mapConcatChunk { list =>
      val a = Chunk.fromJavaIterable(list).flatMap(_.safeRead()).toArray
      ByteArray(a, 0, a.length)
    }

  override protected def cancelLowLevelBody(p: Publisher[ju.List[ByteBuffer]]): Unit = cancelPublisher(p)

  override protected def ensureOnAbnormal[T](effect: Task[T])(finalizer: => Task[Unit]): Task[T] = effect.onExit {
    exit =>
      if (exit.isSuccess) ZIO.unit else finalizer.catchAll(t => ZIO.logErrorCause("Error in finalizer", Cause.fail(t)))
  }.resurrect

  override protected val bodyToHttpClient: BodyToHttpClient[Task, ZioStreams, R] =
    new BodyToHttpClient[Task, ZioStreams, R] {
      override val streams: ZioStreams = ZioStreams
      override implicit def monad: MonadError[Task] = self.monad
      override val multiPartBodyBuilder: MultipartBodyBuilder[streams.BinaryStream, Task] =
        new StreamMultipartBodyBuilder[ZioStreams.BinaryStream, Task] {
          override def fileToStream(file: File): streams.BinaryStream = ZStream.fromFile(file, 8192)
          override def byteArrayToStream(array: Array[Byte]): streams.BinaryStream = ZStream.fromIterable(array)
          override def inputStreamToStream(stream: InputStream): streams.BinaryStream =
            ZStream.fromInputStream(stream, 8192)
          override def concatStreams(
              stream1: streams.BinaryStream,
              stream2: streams.BinaryStream
          ): streams.BinaryStream = stream1.concat(stream2)
          override def toPublisher(stream: streams.BinaryStream): Task[BodyPublisher] = streamToPublisher(stream)
        }
      override def streamToPublisher(stream: ZStream[Any, Throwable, Byte]): Task[BodyPublisher] = {
        import _root_.zio.interop.reactivestreams.{streamToPublisher => zioStreamToPublisher}
        val publisher = stream.mapChunks(byteChunk => Chunk(ByteBuffer.wrap(byteChunk.toArray))).toPublisher
        publisher.map { pub =>
          BodyPublishers.fromPublisher(FlowAdapters.toFlowPublisher(pub))
        }
      }
      override def compressors: List[Compressor[R]] = compressionHandlers.compressors
    }

  override def send[T](request: GenericRequest[T, R]): Task[Response[T]] =
    super.send(request).resurrect

  override protected val bodyFromHttpClient: BodyFromHttpClient[Task, ZioStreams, ZioStreams.BinaryStream] =
    new ZioBodyFromHttpClient

  override protected def createSimpleQueue[T]: Task[SimpleQueue[Task, T]] =
    for {
      runtime <- ZIO.runtime[Any]
      queue <- Queue.unbounded[T]
    } yield new ZioSimpleQueue(queue, runtime)

  override protected def createSequencer: Task[Sequencer[Task]] = ZioSequencer.create

  override protected def bodyToLimitedBody(b: ZioStreams.BinaryStream, limit: Long): ZioStreams.BinaryStream =
    ZioStreams.limitBytes(b, limit)

  override protected def addOnEndCallbackToBody(
      b: ZioStreams.BinaryStream,
      callback: () => Unit
  ): ZioStreams.BinaryStream =
    b.ensuringWith(exit => if (exit.isSuccess) ZIO.attempt(callback()).orDie else ZIO.unit)
}

object HttpClientZioBackend {
  val DefaultCompressionHandlers: CompressionHandlers[ZioStreams, ZioStreams.BinaryStream] =
    CompressionHandlers(
      List(GZipZioCompressor, DeflateZioCompressor),
      List(GZipZioDecompressor, DeflateZioDecompressor)
    )

  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      compressionHandlers: CompressionHandlers[ZioStreams, ZioStreams.BinaryStream]
  ): WebSocketStreamBackend[Task, ZioStreams] =
    wrappers.FollowRedirectsBackend(
      new HttpClientZioBackend(client, closeClient, customizeRequest, compressionHandlers)
    )

  def apply(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[ZioStreams, ZioStreams.BinaryStream] = DefaultCompressionHandlers
  ): Task[WebSocketStreamBackend[Task, ZioStreams]] =
    ZIO.executor.flatMap(executor =>
      ZIO.attempt(
        HttpClientZioBackend(
          HttpClientBackend.defaultClient(options, Some(executor.asJava)),
          closeClient = false, // we don't want to close ZIO's executor
          customizeRequest,
          compressionHandlers
        )
      )
    )

  def scoped(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[ZioStreams, ZioStreams.BinaryStream] = DefaultCompressionHandlers
  ): ZIO[Scope, Throwable, WebSocketStreamBackend[Task, ZioStreams]] =
    ZIO.acquireRelease(apply(options, customizeRequest, compressionHandlers))(
      _.close().ignore
    )

  def scopedUsingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[ZioStreams, ZioStreams.BinaryStream] = DefaultCompressionHandlers
  ): ZIO[Scope, Throwable, WebSocketStreamBackend[Task, ZioStreams]] =
    ZIO.acquireRelease(
      ZIO.attempt(HttpClientZioBackend(client, closeClient = true, customizeRequest, compressionHandlers))
    )(_.close().ignore)

  def layer(
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[ZioStreams, ZioStreams.BinaryStream] = DefaultCompressionHandlers
  ): ZLayer[Any, Throwable, SttpClient] =
    ZLayer.scoped(
      (for {
        backend <- HttpClientZioBackend(
          options,
          customizeRequest,
          compressionHandlers
        )
      } yield backend).tap(client => ZIO.addFinalizer(client.close().ignore))
    )

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[ZioStreams, ZioStreams.BinaryStream] = DefaultCompressionHandlers
  ): WebSocketStreamBackend[Task, ZioStreams] =
    HttpClientZioBackend(
      client,
      closeClient = false,
      customizeRequest,
      compressionHandlers
    )

  def layerUsingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: CompressionHandlers[ZioStreams, ZioStreams.BinaryStream] = DefaultCompressionHandlers
  ): ZLayer[Any, Throwable, SttpClient] =
    ZLayer.scoped(
      ZIO
        .acquireRelease(
          ZIO.attempt(
            usingClient(
              client,
              customizeRequest,
              compressionHandlers
            )
          )
        )(_.close().ignore)
    )

  /** Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports `Stream[Throwable,
    * ByteBuffer]` streaming.
    *
    * See [[WebSocketStreamBackendStub]] for details on how to configure stub responses.
    */
  def stub: WebSocketStreamBackendStub[Task, ZioStreams] = WebSocketStreamBackendStub(new RIOMonadAsyncError[Any])
}
