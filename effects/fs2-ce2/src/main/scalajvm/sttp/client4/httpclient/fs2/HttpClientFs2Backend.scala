package sttp.client4.httpclient.fs2

import java.io.UnsupportedEncodingException
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.ByteBuffer
import java.{util => ju}
import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import fs2.{Chunk, Stream}
import fs2.concurrent.InspectableQueue
import fs2.interop.reactivestreams._
import org.reactivestreams.FlowAdapters
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.httpclient.HttpClientBackend.EncodingHandler
import sttp.client4.httpclient.fs2.HttpClientFs2Backend.Fs2EncodingHandler
import sttp.client4.internal.httpclient.{BodyFromHttpClient, BodyToHttpClient, Sequencer}
import sttp.client4.impl.cats.implicits._
import sttp.client4.impl.fs2.{Fs2Compression, Fs2SimpleQueue}
import sttp.client4.internal.ws.SimpleQueue
import sttp.client4.testing.WebSocketStreamBackendStub
import sttp.client4._
import sttp.client4.httpclient.{HttpClientAsyncBackend, HttpClientBackend}
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.monad.MonadError

import java.net.http.HttpResponse.BodyHandlers
import java.util.concurrent.Flow.Publisher
import scala.collection.JavaConverters._

class HttpClientFs2Backend[F[_]: ConcurrentEffect: ContextShift] private (
    client: HttpClient,
    blocker: Blocker,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: Fs2EncodingHandler[F]
) extends HttpClientAsyncBackend[F, Fs2Streams[F], Publisher[ju.List[ByteBuffer]], Stream[F, Byte]](
      client,
      implicitly,
      closeClient,
      customizeRequest,
      customEncodingHandler
    )
    with WebSocketStreamBackend[F, Fs2Streams[F]] { self =>

  override val streams: Fs2Streams[F] = Fs2Streams[F]

  override def send[T](request: GenericRequest[T, R]): F[Response[T]] =
    super.send(request).guarantee(ContextShift[F].shift)

  override protected val bodyToHttpClient: BodyToHttpClient[F, Fs2Streams[F]] =
    new BodyToHttpClient[F, Fs2Streams[F]] {
      override val streams: Fs2Streams[F] = Fs2Streams[F]
      override implicit def monad: MonadError[F] = self.monad
      override def streamToPublisher(stream: Stream[F, Byte]): F[HttpRequest.BodyPublisher] =
        monad.eval(
          BodyPublishers.fromPublisher(
            FlowAdapters.toFlowPublisher(stream.chunks.map(_.toByteBuffer).toUnicastPublisher)
          )
        )
    }

  override protected def createBodyHandler: HttpResponse.BodyHandler[Publisher[ju.List[ByteBuffer]]] =
    BodyHandlers.ofPublisher()

  override protected val bodyFromHttpClient: BodyFromHttpClient[F, Fs2Streams[F], Stream[F, Byte]] =
    new Fs2BodyFromHttpClient[F](blocker)

  override protected def createSimpleQueue[T]: F[SimpleQueue[F, T]] =
    InspectableQueue.unbounded[F, T].map(new Fs2SimpleQueue(_, None))

  override protected def createSequencer: F[Sequencer[F]] = Fs2Sequencer.create

  override protected def bodyHandlerBodyToBody(p: Publisher[ju.List[ByteBuffer]]): Stream[F, Byte] = {
    FlowAdapters
      .toPublisher(p)
      .toStream[F]
      .flatMap(data => Stream.emits(data.asScala.map(Chunk.byteBuffer)).flatMap(Stream.chunk))
  }

  override protected def emptyBody(): Stream[F, Byte] = Stream.empty

  override protected def standardEncoding: (Stream[F, Byte], String) => Stream[F, Byte] = {
    case (body, "gzip")    => body.through(fs2.compression.gunzip()).flatMap(_.content)
    case (body, "deflate") => body.through(Fs2Compression.inflateCheckHeader)
    case (_, ce)           => Stream.raiseError[F](new UnsupportedEncodingException(s"Unsupported encoding: $ce"))
  }
}

object HttpClientFs2Backend {
  type Fs2EncodingHandler[F[_]] = EncodingHandler[Stream[F, Byte]]

  private def apply[F[_]: ConcurrentEffect: ContextShift](
      client: HttpClient,
      blocker: Blocker,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      customEncodingHandler: Fs2EncodingHandler[F]
  ): WebSocketStreamBackend[F, Fs2Streams[F]] =
    FollowRedirectsBackend(
      new HttpClientFs2Backend(client, blocker, closeClient, customizeRequest, customEncodingHandler)
    )

  def apply[F[_]: ConcurrentEffect: ContextShift](
                                                   blocker: Blocker,
                                                   options: BackendOptions = BackendOptions.Default,
                                                   customizeRequest: HttpRequest => HttpRequest = identity,
                                                   customEncodingHandler: Fs2EncodingHandler[F] = PartialFunction.empty
  ): F[WebSocketStreamBackend[F, Fs2Streams[F]]] =
    Sync[F].delay(
      HttpClientFs2Backend(
        HttpClientBackend.defaultClient(options, None),
        blocker,
        closeClient = true,
        customizeRequest,
        customEncodingHandler
      )
    )

  def resource[F[_]: ConcurrentEffect: ContextShift](
                                                      blocker: Blocker,
                                                      options: BackendOptions = BackendOptions.Default,
                                                      customizeRequest: HttpRequest => HttpRequest = identity,
                                                      customEncodingHandler: Fs2EncodingHandler[F] = PartialFunction.empty
  ): Resource[F, WebSocketStreamBackend[F, Fs2Streams[F]]] =
    Resource.make(apply(blocker, options, customizeRequest, customEncodingHandler))(_.close())

  def resourceUsingClient[F[_]: ConcurrentEffect: ContextShift](
      client: HttpClient,
      blocker: Blocker,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: Fs2EncodingHandler[F] = PartialFunction.empty
  ): Resource[F, WebSocketStreamBackend[F, Fs2Streams[F]]] =
    Resource.make(
      Sync[F].delay(HttpClientFs2Backend(client, blocker, closeClient = true, customizeRequest, customEncodingHandler))
    )(_.close())

  def usingClient[F[_]: ConcurrentEffect: ContextShift](
      client: HttpClient,
      blocker: Blocker,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: Fs2EncodingHandler[F] = PartialFunction.empty
  ): WebSocketStreamBackend[F, Fs2Streams[F]] =
    HttpClientFs2Backend(client, blocker, closeClient = false, customizeRequest, customEncodingHandler)

  /** Create a stub backend for testing, which uses the [[F]] response wrapper, and supports `Stream[F, Byte]`
    * streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Concurrent]: WebSocketStreamBackendStub[F, Fs2Streams[F]] = WebSocketStreamBackendStub(implicitly)
}
