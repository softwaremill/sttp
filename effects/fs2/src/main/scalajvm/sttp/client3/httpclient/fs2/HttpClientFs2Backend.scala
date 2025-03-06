package sttp.client3.httpclient.fs2

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
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.internal.httpclient.cancelPublisher
import sttp.client3.HttpClientBackend.EncodingHandler
import sttp.client3.httpclient.fs2.HttpClientFs2Backend.Fs2EncodingHandler
import sttp.client3.internal.httpclient.{BodyFromHttpClient, BodyToHttpClient, Sequencer}
import sttp.client3.impl.cats.implicits._
import sttp.client3.impl.fs2.Fs2SimpleQueue
import sttp.client3.internal.ws.SimpleQueue
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{
  FollowRedirectsBackend,
  HttpClientAsyncBackend,
  HttpClientBackend,
  Request,
  Response,
  SttpBackend,
  SttpBackendOptions
}
import sttp.monad.MonadError

import java.net.http.HttpResponse.BodyHandlers
import java.util.concurrent.Flow.Publisher
import java.{util => ju}
import scala.collection.JavaConverters._

class HttpClientFs2Backend[F[_]: Async] private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: Fs2EncodingHandler[F],
    dispatcher: Dispatcher[F]
) extends HttpClientAsyncBackend[F, Fs2Streams[F], Fs2Streams[F] with WebSockets, Publisher[
      ju.List[ByteBuffer]
    ], Stream[F, Byte]](
      client,
      implicitly,
      closeClient,
      customizeRequest,
      customEncodingHandler
    ) {

  override val streams: Fs2Streams[F] = Fs2Streams[F]

  override def send[T, R >: PE](request: Request[T, R]): F[Response[T]] =
    super.send(request)

  override protected val bodyToHttpClient: BodyToHttpClient[F, Fs2Streams[F]] =
    new BodyToHttpClient[F, Fs2Streams[F]] {
      override val streams: Fs2Streams[F] = Fs2Streams[F]
      override implicit def monad: MonadError[F] = responseMonad
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

  override protected def lowLevelBodyToBody(p: Publisher[util.List[ByteBuffer]]): Stream[F, Byte] = {
    FlowAdapters
      .toPublisher(p)
      .toStream[F]
      .flatMap(data => Stream.emits(data.asScala.map(Chunk.byteBuffer)).flatMap(Stream.chunk))
  }

  override protected def cancelLowLevelBody(p: Publisher[ju.List[ByteBuffer]]): Unit = cancelPublisher(p)

  override protected def ensureOnAbnormal[T](effect: F[T])(finalizer: => F[Unit]): F[T] = {
    Async[F].guaranteeCase(effect) { outcome =>
      if (outcome.isSuccess) Async[F].unit
      else Async[F].onError(finalizer) { case t => Async[F].delay(t.printStackTrace()) }
    }
  }

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
  ): SttpBackend[F, Fs2Streams[F] with WebSockets] =
    new FollowRedirectsBackend(
      new HttpClientFs2Backend(client, closeClient, customizeRequest, customEncodingHandler, dispatcher)
    )

  def apply[F[_]: Async](
      dispatcher: Dispatcher[F],
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: Fs2EncodingHandler[F] = PartialFunction.empty
  ): F[SttpBackend[F, Fs2Streams[F] with WebSockets]] = {
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
  }

  def resource[F[_]: Async](
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: Fs2EncodingHandler[F] = PartialFunction.empty
  ): Resource[F, SttpBackend[F, Fs2Streams[F] with WebSockets]] =
    Dispatcher
      .parallel[F]
      .flatMap(dispatcher =>
        Resource.make(apply(dispatcher, options, customizeRequest, customEncodingHandler))(_.close())
      )

  def resourceUsingClient[F[_]: Async](
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: Fs2EncodingHandler[F] = PartialFunction.empty
  ): Resource[F, SttpBackend[F, Fs2Streams[F] with WebSockets]] =
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
  ): SttpBackend[F, Fs2Streams[F] with WebSockets] =
    HttpClientFs2Backend(client, closeClient = false, customizeRequest, customEncodingHandler, dispatcher)

  /** Create a stub backend for testing, which uses the [[F]] response wrapper, and supports `Stream[F, Byte]`
    * streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Async]: SttpBackendStub[F, Fs2Streams[F] with WebSockets] = SttpBackendStub(implicitly)
}
