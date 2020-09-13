package sttp.client.httpclient.fs2

import java.io.UnsupportedEncodingException
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.{HttpClient, HttpRequest}
import java.nio.ByteBuffer
import java.util

import cats.effect._
import cats.effect.implicits._
import cats.implicits._
import fs2.Stream
import fs2.concurrent.InspectableQueue
import fs2.interop.reactivestreams._
import org.reactivestreams.{FlowAdapters, Publisher}
import sttp.capabilities.WebSockets
import sttp.capabilities.fs2.Fs2Streams
import sttp.client.httpclient.HttpClientBackend.EncodingHandler
import sttp.client.httpclient.fs2.HttpClientFs2Backend.Fs2EncodingHandler
import sttp.client.httpclient.{
  BodyFromHttpClient,
  BodyToHttpClient,
  HttpClientAsyncBackend,
  HttpClientBackend,
  RichByteBuffer
}
import sttp.client.impl.cats.implicits._
import sttp.client.impl.fs2.Fs2SimpleQueue
import sttp.client.internal.ws.SimpleQueue
import sttp.client.testing.SttpBackendStub
import sttp.client.{FollowRedirectsBackend, Request, Response, SttpBackend, SttpBackendOptions}
import sttp.monad.MonadError

import scala.collection.JavaConverters._

class HttpClientFs2Backend[F[_]: ConcurrentEffect: ContextShift] private (
    client: HttpClient,
    blocker: Blocker,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: Fs2EncodingHandler[F]
) extends HttpClientAsyncBackend[F, Fs2Streams[F], Fs2Streams[F] with WebSockets, Stream[F, Byte]](
      client,
      implicitly,
      closeClient,
      customizeRequest,
      customEncodingHandler
    ) {

  override val streams: Fs2Streams[F] = Fs2Streams[F]

  override def send[T, R >: PE](request: Request[T, R]): F[Response[T]] =
    super.send(request).guarantee(ContextShift[F].shift)

  override protected val bodyToHttpClient: BodyToHttpClient[F, Fs2Streams[F]] =
    new BodyToHttpClient[F, Fs2Streams[F]] {
      override val streams: Fs2Streams[F] = Fs2Streams[F]
      override implicit def monad: MonadError[F] = responseMonad
      override def streamToPublisher(stream: Stream[F, Byte]): F[HttpRequest.BodyPublisher] =
        monad.eval(
          BodyPublishers.fromPublisher(
            FlowAdapters.toFlowPublisher(stream.chunks.map(_.toByteBuffer).toUnicastPublisher())
          )
        )
    }

  override protected val bodyFromHttpClient: BodyFromHttpClient[F, Fs2Streams[F], Stream[F, Byte]] =
    new Fs2BodyFromHttpClient[F](blocker)

  override protected def createSimpleQueue[T]: F[SimpleQueue[F, T]] =
    InspectableQueue.unbounded[F, T].map(new Fs2SimpleQueue(_, None))

  override protected def publisherToBody(p: Publisher[util.List[ByteBuffer]]): Stream[F, Byte] = {
    p.toStream[F].flatMap(data => Stream.emits(data.asScala.flatMap(_.safeRead()))) //TODO take a closer look
  }

  override protected def emptyBody(): Stream[F, Byte] = Stream.empty

  override protected def standardEncoding: (Stream[F, Byte], String) => Stream[F, Byte] = {
    case (body, "gzip")    => body.through(fs2.compression.gunzip()).flatMap(_.content)
    case (body, "deflate") => body.through(fs2.compression.inflate())
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
  ): SttpBackend[F, Fs2Streams[F] with WebSockets] =
    new FollowRedirectsBackend(
      new HttpClientFs2Backend(client, blocker, closeClient, customizeRequest, customEncodingHandler)
    )

  def apply[F[_]: ConcurrentEffect: ContextShift](
      blocker: Blocker,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: Fs2EncodingHandler[F] = PartialFunction.empty
  ): F[SttpBackend[F, Fs2Streams[F] with WebSockets]] =
    Sync[F].delay(
      HttpClientFs2Backend(
        HttpClientBackend.defaultClient(options),
        blocker,
        closeClient = true,
        customizeRequest,
        customEncodingHandler
      )
    )

  def resource[F[_]: ConcurrentEffect: ContextShift](
      blocker: Blocker,
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: Fs2EncodingHandler[F] = PartialFunction.empty
  ): Resource[F, SttpBackend[F, Fs2Streams[F] with WebSockets]] =
    Resource.make(apply(blocker, options, customizeRequest, customEncodingHandler))(_.close())

  def usingClient[F[_]: ConcurrentEffect: ContextShift](
      client: HttpClient,
      blocker: Blocker,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: Fs2EncodingHandler[F] = PartialFunction.empty
  ): SttpBackend[F, Fs2Streams[F] with WebSockets] =
    HttpClientFs2Backend(client, blocker, closeClient = false, customizeRequest, customEncodingHandler)

  /**
    * Create a stub backend for testing, which uses the [[F]] response wrapper, and supports `Stream[F, Byte]`
    * streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub[F[_]: Concurrent]: SttpBackendStub[F, Fs2Streams[F] with WebSockets] = SttpBackendStub(implicitly)
}
