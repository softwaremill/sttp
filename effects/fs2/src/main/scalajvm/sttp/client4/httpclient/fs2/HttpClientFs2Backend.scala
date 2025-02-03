package sttp.client4.httpclient.fs2

import cats.effect.kernel._
import cats.effect.std.Dispatcher
import cats.effect.std.Queue
import cats.implicits._
import fs2.Chunk
import fs2.Stream
import fs2.compression.Compression
import fs2.interop.reactivestreams.PublisherOps
import fs2.interop.reactivestreams.StreamUnicastPublisher
import org.reactivestreams.FlowAdapters
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4._
import sttp.client4.compression.CompressionHandlers
import sttp.client4.compression.Compressor
import sttp.client4.httpclient.HttpClientAsyncBackend
import sttp.client4.httpclient.HttpClientBackend
import sttp.client4.impl.cats.implicits._
import sttp.client4.impl.fs2.DeflateFs2Compressor
import sttp.client4.impl.fs2.DeflateFs2Decompressor
import sttp.client4.impl.fs2.Fs2SimpleQueue
import sttp.client4.impl.fs2.GZipFs2Compressor
import sttp.client4.impl.fs2.GZipFs2Decompressor
import sttp.client4.internal.httpclient.BodyFromHttpClient
import sttp.client4.internal.httpclient.BodyToHttpClient
import sttp.client4.internal.httpclient.Sequencer
import sttp.client4.internal.httpclient.cancelPublisher
import sttp.client4.internal.ws.SimpleQueue
import sttp.client4.testing.WebSocketStreamBackendStub
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.monad.MonadError

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandlers
import java.nio.ByteBuffer
import java.util
import java.{util => ju}
import java.util.concurrent.Flow.Publisher
import scala.collection.JavaConverters._
import cats.effect.kernel.Resource.ExitCase

class HttpClientFs2Backend[F[_]: Async] private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    compressionHandlers: CompressionHandlers[Fs2Streams[F], Stream[F, Byte]],
    dispatcher: Dispatcher[F]
) extends HttpClientAsyncBackend[F, Fs2Streams[F], Publisher[ju.List[ByteBuffer]], Stream[F, Byte]](
      client,
      implicitly,
      closeClient,
      customizeRequest,
      compressionHandlers
    )
    with WebSocketStreamBackend[F, Fs2Streams[F]] { self =>

  override val streams: Fs2Streams[F] = Fs2Streams[F]

  override protected val bodyToHttpClient: BodyToHttpClient[F, Fs2Streams[F], R] =
    new BodyToHttpClient[F, Fs2Streams[F], R] {
      override val streams: Fs2Streams[F] = Fs2Streams[F]
      override implicit def monad: MonadError[F] = self.monad
      override def compressors: List[Compressor[R]] = compressionHandlers.compressors
      override def streamToPublisher(stream: Stream[F, Byte]): F[HttpRequest.BodyPublisher] =
        monad.eval(
          BodyPublishers.fromPublisher(
            FlowAdapters.toFlowPublisher(
              StreamUnicastPublisher(stream.chunks.map(_.toByteBuffer), dispatcher): org.reactivestreams.Publisher[
                ByteBuffer
              ]
            )
          )
        )
    }

  override protected def createBodyHandler: HttpResponse.BodyHandler[Publisher[util.List[ByteBuffer]]] =
    BodyHandlers.ofPublisher()

  override protected val bodyFromHttpClient: BodyFromHttpClient[F, Fs2Streams[F], Stream[F, Byte]] =
    new Fs2BodyFromHttpClient[F]()

  override protected def createSimpleQueue[T]: F[SimpleQueue[F, T]] =
    Queue.unbounded[F, T].map(new Fs2SimpleQueue(_, None, dispatcher))

  override protected def createSequencer: F[Sequencer[F]] = Fs2Sequencer.create

  override protected def lowLevelBodyToBody(p: Publisher[util.List[ByteBuffer]]): Stream[F, Byte] =
    FlowAdapters
      .toPublisher(p)
      .toStream[F]
      .flatMap(data => Stream.emits(data.asScala.map(Chunk.byteBuffer)).flatMap(Stream.chunk))

  override protected def cancelLowLevelBody(p: Publisher[ju.List[ByteBuffer]]): Unit = cancelPublisher(p)

  override protected def ensureOnAbnormal[T](effect: F[T])(finalizer: => F[Unit]): F[T] =
    Async[F].guaranteeCase(effect) { outcome =>
      if (outcome.isSuccess) Async[F].unit
      else Async[F].onError(finalizer) { case t => Async[F].delay(t.printStackTrace()) }
    }

  override protected def emptyBody(): Stream[F, Byte] = Stream.empty

  override protected def bodyToLimitedBody(b: Stream[F, Byte], limit: Long): Stream[F, Byte] =
    Fs2Streams.limitBytes(b, limit)

  override protected def addOnEndCallbackToBody(b: Stream[F, Byte], callback: () => Unit): Stream[F, Byte] =
    b.onFinalizeCase(exitCase => if (exitCase == ExitCase.Succeeded) Async[F].delay(callback()) else Async[F].unit)
}

object HttpClientFs2Backend {
  def defaultCompressionHandlers[F[_]: Async]: CompressionHandlers[Fs2Streams[F], Stream[F, Byte]] =
    CompressionHandlers(
      List(new GZipFs2Compressor[F, Fs2Streams[F]](), new DeflateFs2Compressor[F, Fs2Streams[F]]()),
      List(new GZipFs2Decompressor, new DeflateFs2Decompressor)
    )

  private def apply[F[_]: Async](
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      compressionHandlers: CompressionHandlers[Fs2Streams[F], Stream[F, Byte]],
      dispatcher: Dispatcher[F]
  ): WebSocketStreamBackend[F, Fs2Streams[F]] =
    FollowRedirectsBackend(
      new HttpClientFs2Backend(client, closeClient, customizeRequest, compressionHandlers, dispatcher)
    )

  def apply[F[_]: Async](
      dispatcher: Dispatcher[F],
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: Async[F] => CompressionHandlers[Fs2Streams[F], Stream[F, Byte]] =
        defaultCompressionHandlers[F](_: Async[F])
  ): F[WebSocketStreamBackend[F, Fs2Streams[F]]] =
    Async[F].executor.flatMap(executor =>
      Sync[F].delay(
        HttpClientFs2Backend(
          HttpClientBackend.defaultClient(options, Some(executor)),
          closeClient = false, // we don't want to close the underlying executor
          customizeRequest,
          compressionHandlers(implicitly),
          dispatcher
        )
      )
    )

  def resource[F[_]: Async](
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: Async[F] => CompressionHandlers[Fs2Streams[F], Stream[F, Byte]] =
        defaultCompressionHandlers[F](_: Async[F])
  ): Resource[F, WebSocketStreamBackend[F, Fs2Streams[F]]] =
    Dispatcher
      .parallel[F]
      .flatMap(dispatcher =>
        Resource.make(apply(dispatcher, options, customizeRequest, compressionHandlers))(_.close())
      )

  def resourceUsingClient[F[_]: Async](
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: Async[F] => CompressionHandlers[Fs2Streams[F], Stream[F, Byte]] =
        defaultCompressionHandlers[F](_: Async[F])
  ): Resource[F, WebSocketStreamBackend[F, Fs2Streams[F]]] =
    Dispatcher
      .parallel[F]
      .flatMap(dispatcher =>
        Resource.make(
          Sync[F]
            .delay(apply(client, closeClient = true, customizeRequest, compressionHandlers(implicitly), dispatcher))
        )(_.close())
      )

  def usingClient[F[_]: Async](
      client: HttpClient,
      dispatcher: Dispatcher[F],
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: Async[F] => CompressionHandlers[Fs2Streams[F], Stream[F, Byte]] =
        defaultCompressionHandlers[F](_: Async[F])
  ): WebSocketStreamBackend[F, Fs2Streams[F]] =
    HttpClientFs2Backend(client, closeClient = false, customizeRequest, compressionHandlers(implicitly), dispatcher)

  /** Create a stub backend for testing, which uses the [[F]] response wrapper, and supports `Stream[F, Byte]`
    * streaming.
    *
    * See [[sttp.client4.testing.BackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Async]: WebSocketStreamBackendStub[F, Fs2Streams[F]] = WebSocketStreamBackendStub(implicitly)
}
