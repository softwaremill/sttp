package sttp.client4.httpclient.fs2

import java.io.UnsupportedEncodingException
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.ByteBuffer
import java.util
import cats.effect.kernel._
import cats.effect.std.{Dispatcher, Queue}
import cats.implicits._
import fs2.interop.reactivestreams.{PublisherOps, StreamUnicastPublisher}
import fs2.{Chunk, Stream}
import org.reactivestreams.FlowAdapters
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.httpclient.HttpClientBackend.EncodingHandler
import sttp.client4.httpclient.fs2.HttpClientFs2Backend.Fs2EncodingHandler
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
import java.{util => ju}
import scala.collection.JavaConverters._
import sttp.client4.internal.compression.Compressor
import sttp.client4.httpclient.fs2.compression.{DeflateFs2Compressor, GZipFs2Compressor}

class HttpClientFs2Backend[F[_]: Async] private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: Fs2EncodingHandler[F],
    dispatcher: Dispatcher[F]
) extends HttpClientAsyncBackend[F, Fs2Streams[F], Publisher[ju.List[ByteBuffer]], Stream[F, Byte]](
      client,
      implicitly,
      closeClient,
      customizeRequest,
      customEncodingHandler
    )
    with WebSocketStreamBackend[F, Fs2Streams[F]] { self =>

  override val streams: Fs2Streams[F] = Fs2Streams[F]

  override protected val bodyToHttpClient: BodyToHttpClient[F, Fs2Streams[F], R] =
    new BodyToHttpClient[F, Fs2Streams[F], R] {
      override val streams: Fs2Streams[F] = Fs2Streams[F]
      override implicit def monad: MonadError[F] = self.monad
      override def compressors: List[Compressor[R]] =
        List(new GZipFs2Compressor[F, R](), new DeflateFs2Compressor[F, R]())
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

  override protected def bodyHandlerBodyToBody(p: Publisher[util.List[ByteBuffer]]): Stream[F, Byte] =
    FlowAdapters
      .toPublisher(p)
      .toStream[F]
      .flatMap(data => Stream.emits(data.asScala.map(Chunk.byteBuffer)).flatMap(Stream.chunk))

  override protected def emptyBody(): Stream[F, Byte] = Stream.empty

  override protected def standardEncoding: (Stream[F, Byte], String) => Stream[F, Byte] = {
    case (body, "gzip")    => body.through(fs2.compression.Compression[F].gunzip()).flatMap(_.content)
    case (body, "deflate") => body.through(Fs2Compression.inflateCheckHeader[F])
    case (_, ce)           => Stream.raiseError[F](new UnsupportedEncodingException(s"Unsupported encoding: $ce"))
  }
}

object HttpClientFs2Backend {
  type Fs2EncodingHandler[F[_]] = EncodingHandler[Stream[F, Byte]]

  private def apply[F[_]: Async](
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      customEncodingHandler: Fs2EncodingHandler[F],
      dispatcher: Dispatcher[F]
  ): WebSocketStreamBackend[F, Fs2Streams[F]] =
    FollowRedirectsBackend(
      new HttpClientFs2Backend(client, closeClient, customizeRequest, customEncodingHandler, dispatcher)
    )

  def apply[F[_]: Async](
      dispatcher: Dispatcher[F],
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: Fs2EncodingHandler[F] = PartialFunction.empty
  ): F[WebSocketStreamBackend[F, Fs2Streams[F]]] =
    Async[F].executor.flatMap(executor =>
      Sync[F].delay(
        HttpClientFs2Backend(
          HttpClientBackend.defaultClient(options, Some(executor)),
          closeClient = false, // we don't want to close the underlying executor
          customizeRequest,
          customEncodingHandler,
          dispatcher
        )
      )
    )

  def resource[F[_]: Async](
      options: BackendOptions = BackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: Fs2EncodingHandler[F] = PartialFunction.empty
  ): Resource[F, WebSocketStreamBackend[F, Fs2Streams[F]]] =
    Dispatcher
      .parallel[F]
      .flatMap(dispatcher =>
        Resource.make(apply(dispatcher, options, customizeRequest, customEncodingHandler))(_.close())
      )

  def resourceUsingClient[F[_]: Async](
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: Fs2EncodingHandler[F] = PartialFunction.empty
  ): Resource[F, WebSocketStreamBackend[F, Fs2Streams[F]]] =
    Dispatcher
      .parallel[F]
      .flatMap(dispatcher =>
        Resource.make(
          Sync[F].delay(apply(client, closeClient = true, customizeRequest, customEncodingHandler, dispatcher))
        )(_.close())
      )

  def usingClient[F[_]: Async](
      client: HttpClient,
      dispatcher: Dispatcher[F],
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: Fs2EncodingHandler[F] = PartialFunction.empty
  ): WebSocketStreamBackend[F, Fs2Streams[F]] =
    HttpClientFs2Backend(client, closeClient = false, customizeRequest, customEncodingHandler, dispatcher)

  /** Create a stub backend for testing, which uses the [[F]] response wrapper, and supports `Stream[F, Byte]`
    * streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Async]: WebSocketStreamBackendStub[F, Fs2Streams[F]] = WebSocketStreamBackendStub(implicitly)
}
