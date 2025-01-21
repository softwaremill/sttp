package sttp.client4.httpclient.fs2

import java.net.http.HttpRequest.BodyPublishers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.ByteBuffer
import java.{util => ju}
import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import fs2.{Chunk, Stream, Pull}
import fs2.concurrent.InspectableQueue
import fs2.interop.reactivestreams._
import org.reactivestreams.FlowAdapters
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.internal.httpclient.{BodyFromHttpClient, BodyToHttpClient, Sequencer}
import sttp.client4.impl.cats.implicits._
import sttp.client4.impl.fs2.Fs2SimpleQueue
import sttp.client4.internal.ws.SimpleQueue
import sttp.client4.testing.WebSocketStreamBackendStub
import sttp.client4._
import sttp.client4.httpclient.{HttpClientAsyncBackend, HttpClientBackend}
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.monad.MonadError

import java.net.http.HttpResponse.BodyHandlers
import java.util.concurrent.Flow.Publisher
import scala.collection.JavaConverters._
import sttp.client4.compression.CompressionHandlers
import sttp.client4.compression.Compressor
import sttp.client4.impl.fs2.GZipFs2Decompressor
import sttp.client4.impl.fs2.DeflateFs2Decompressor
import sttp.capabilities.StreamMaxLengthExceededException

class HttpClientFs2Backend[F[_]: ConcurrentEffect: ContextShift] private (
    client: HttpClient,
    blocker: Blocker,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    compressionHandlers: CompressionHandlers[Fs2Streams[F], Stream[F, Byte]]
) extends HttpClientAsyncBackend[F, Fs2Streams[F], Publisher[ju.List[ByteBuffer]], Stream[F, Byte]](
      client,
      implicitly,
      closeClient,
      customizeRequest,
      compressionHandlers
    )
    with WebSocketStreamBackend[F, Fs2Streams[F]] { self =>

  override val streams: Fs2Streams[F] = Fs2Streams[F]

  override def send[T](request: GenericRequest[T, R]): F[Response[T]] =
    super.send(request).guarantee(ContextShift[F].shift)

  override protected val bodyToHttpClient: BodyToHttpClient[F, Fs2Streams[F], R] =
    new BodyToHttpClient[F, Fs2Streams[F], R] {
      override val streams: Fs2Streams[F] = Fs2Streams[F]
      override implicit def monad: MonadError[F] = self.monad
      override def streamToPublisher(stream: Stream[F, Byte]): F[HttpRequest.BodyPublisher] =
        monad.eval(
          BodyPublishers.fromPublisher(
            FlowAdapters.toFlowPublisher(stream.chunks.map(_.toByteBuffer).toUnicastPublisher)
          )
        )
      override def compressors: List[Compressor[R]] = compressionHandlers.compressors
    }

  override protected def createBodyHandler: HttpResponse.BodyHandler[Publisher[ju.List[ByteBuffer]]] =
    BodyHandlers.ofPublisher()

  override protected val bodyFromHttpClient: BodyFromHttpClient[F, Fs2Streams[F], Stream[F, Byte]] =
    new Fs2BodyFromHttpClient[F](blocker)

  override protected def createSimpleQueue[T]: F[SimpleQueue[F, T]] =
    InspectableQueue.unbounded[F, T].map(new Fs2SimpleQueue(_, None))

  override protected def createSequencer: F[Sequencer[F]] = Fs2Sequencer.create

  override protected def bodyHandlerBodyToBody(p: Publisher[ju.List[ByteBuffer]]): Stream[F, Byte] =
    FlowAdapters
      .toPublisher(p)
      .toStream[F]
      .flatMap(data => Stream.emits(data.asScala.map(Chunk.byteBuffer)).flatMap(Stream.chunk))

  override protected def emptyBody(): Stream[F, Byte] = Stream.empty

  override protected def bodyToLimitedBody(b: Stream[F, Byte], limit: Long): Stream[F, Byte] = limitBytes(b, limit)

  // based on Fs2Streams.limitBytes (for ce3)
  private def limitBytes[F[_]](stream: Stream[F, Byte], maxBytes: Long): Stream[F, Byte] = {
    def go(s: Stream[F, Byte], remaining: Long): Pull[F, Byte, Unit] = {
      if (remaining < 0) throw new StreamMaxLengthExceededException(maxBytes)
      else
        s.pull.uncons.flatMap {
          case Some((chunk, tail)) =>
            val chunkSize = chunk.size.toLong
            if (chunkSize <= remaining)
              Pull.output(chunk) >> go(tail, remaining - chunkSize)
            else
              throw new StreamMaxLengthExceededException(maxBytes)
          case None => Pull.done
        }
    }
    go(stream, maxBytes).stream
  }
}

object HttpClientFs2Backend {
  def defaultCompressionHandlers[F[_]: Sync]: CompressionHandlers[Fs2Streams[F], Stream[F, Byte]] =
    CompressionHandlers(
      Compressor.default[Fs2Streams[F]],
      List(new GZipFs2Decompressor, new DeflateFs2Decompressor)
    )

  private def apply[F[_]: ConcurrentEffect: ContextShift](
      client: HttpClient,
      blocker: Blocker,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      compressionHandlers: CompressionHandlers[Fs2Streams[F], Stream[F, Byte]]
  ): WebSocketStreamBackend[F, Fs2Streams[F]] =
    FollowRedirectsBackend(
      new HttpClientFs2Backend(client, blocker, closeClient, customizeRequest, compressionHandlers)
    )

  def apply[F[_]: ConcurrentEffect: ContextShift](
      blocker: Blocker,
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: Sync[F] => CompressionHandlers[Fs2Streams[F], Stream[F, Byte]] =
        defaultCompressionHandlers[F](_: Sync[F])
  ): F[WebSocketStreamBackend[F, Fs2Streams[F]]] =
    Sync[F].delay(
      HttpClientFs2Backend(
        HttpClientBackend.defaultClient(options, None),
        blocker,
        closeClient = true,
        customizeRequest,
        compressionHandlers(implicitly)
      )
    )

  def resource[F[_]: ConcurrentEffect: ContextShift](
      blocker: Blocker,
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: Sync[F] => CompressionHandlers[Fs2Streams[F], Stream[F, Byte]] =
        defaultCompressionHandlers[F](_: Sync[F])
  ): Resource[F, WebSocketStreamBackend[F, Fs2Streams[F]]] =
    Resource.make(apply(blocker, options, customizeRequest, compressionHandlers))(_.close())

  def resourceUsingClient[F[_]: ConcurrentEffect: ContextShift](
      client: HttpClient,
      blocker: Blocker,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: Sync[F] => CompressionHandlers[Fs2Streams[F], Stream[F, Byte]] =
        defaultCompressionHandlers[F](_: Sync[F])
  ): Resource[F, WebSocketStreamBackend[F, Fs2Streams[F]]] =
    Resource.make(
      Sync[F].delay(
        HttpClientFs2Backend(client, blocker, closeClient = true, customizeRequest, compressionHandlers(implicitly))
      )
    )(_.close())

  def usingClient[F[_]: ConcurrentEffect: ContextShift](
      client: HttpClient,
      blocker: Blocker,
      customizeRequest: HttpRequest => HttpRequest = identity,
      compressionHandlers: Sync[F] => CompressionHandlers[Fs2Streams[F], Stream[F, Byte]] =
        defaultCompressionHandlers[F](_: Sync[F])
  ): WebSocketStreamBackend[F, Fs2Streams[F]] =
    HttpClientFs2Backend(client, blocker, closeClient = false, customizeRequest, compressionHandlers(implicitly))

  /** Create a stub backend for testing, which uses the [[F]] response wrapper, and supports `Stream[F, Byte]`
    * streaming.
    *
    * See [[sttp.client4.testing.BackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Concurrent]: WebSocketStreamBackendStub[F, Fs2Streams[F]] = WebSocketStreamBackendStub(implicitly)
}
