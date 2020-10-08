package sttp.client3.httpclient.monix

import java.io.UnsupportedEncodingException
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.{HttpClient, HttpRequest}
import java.nio.ByteBuffer
import java.util

import cats.effect.Resource
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import monix.reactive.compression._
import org.reactivestreams.{FlowAdapters, Publisher}
import sttp.capabilities.WebSockets
import sttp.capabilities.monix.MonixStreams
import sttp.client3.httpclient.HttpClientBackend.EncodingHandler
import sttp.client3.httpclient._
import sttp.client3.httpclient.monix.HttpClientMonixBackend.MonixEncodingHandler
import sttp.client3.impl.monix.{MonixSimpleQueue, TaskMonadAsyncError}
import sttp.client3.internal._
import sttp.client3.internal.ws.SimpleQueue
import sttp.client3.testing.SttpBackendStub
import sttp.client3.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import sttp.monad.MonadError

import scala.collection.JavaConverters._

class HttpClientMonixBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: MonixEncodingHandler
)(implicit s: Scheduler)
    extends HttpClientAsyncBackend[Task, MonixStreams, MonixStreams with WebSockets, MonixStreams.BinaryStream](
      client,
      TaskMonadAsyncError,
      closeClient,
      customizeRequest,
      customEncodingHandler
    ) {

  override val streams: MonixStreams = MonixStreams

  override protected val bodyToHttpClient: BodyToHttpClient[Task, MonixStreams] =
    new BodyToHttpClient[Task, MonixStreams] {
      override val streams: MonixStreams = MonixStreams
      override implicit def monad: MonadError[Task] = responseMonad
      override def streamToPublisher(stream: Observable[ByteBuffer]): Task[HttpRequest.BodyPublisher] =
        monad.eval(BodyPublishers.fromPublisher(FlowAdapters.toFlowPublisher(stream.toReactivePublisher)))
    }

  override protected val bodyFromHttpClient: BodyFromHttpClient[Task, MonixStreams, MonixStreams.BinaryStream] =
    new MonixBodyFromHttpClient {
      override implicit def scheduler: Scheduler = s
      override implicit def monad: MonadError[Task] = responseMonad
    }

  override protected def createSimpleQueue[T]: Task[SimpleQueue[Task, T]] =
    Task.eval(new MonixSimpleQueue[T](None))

  override protected def publisherToBody(p: Publisher[util.List[ByteBuffer]]): Observable[ByteBuffer] = {
    Observable
      .fromReactivePublisher(p)
      .flatMapIterable(_.asScala.toList)
  }

  override protected def emptyBody(): Observable[ByteBuffer] = Observable.empty

  override protected def standardEncoding: (Observable[ByteBuffer], String) => Observable[ByteBuffer] = {
    case (body, "gzip")    => body.map(_.safeRead()).transform(gunzip()).map(ByteBuffer.wrap)
    case (body, "deflate") => body.map(_.safeRead()).transform(inflate()).map(ByteBuffer.wrap)
    case (_, ce)           => throw new UnsupportedEncodingException(s"Unsupported encoding: $ce")
  }
}

object HttpClientMonixBackend {
  type MonixEncodingHandler = EncodingHandler[MonixStreams.BinaryStream]

  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      customEncodingHandler: MonixEncodingHandler
  )(implicit
      s: Scheduler
  ): SttpBackend[Task, MonixStreams with WebSockets] =
    new FollowRedirectsBackend(
      new HttpClientMonixBackend(client, closeClient, customizeRequest, customEncodingHandler)(s)
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: MonixEncodingHandler = PartialFunction.empty
  )(implicit
      s: Scheduler = Scheduler.global
  ): Task[SttpBackend[Task, MonixStreams with WebSockets]] =
    Task.eval(
      HttpClientMonixBackend(
        HttpClientBackend.defaultClient(options),
        closeClient = true,
        customizeRequest,
        customEncodingHandler
      )(s)
    )

  def resource(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: MonixEncodingHandler = PartialFunction.empty
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, SttpBackend[Task, MonixStreams with WebSockets]] =
    Resource.make(apply(options, customizeRequest, customEncodingHandler))(_.close())

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: MonixEncodingHandler = PartialFunction.empty
  )(implicit s: Scheduler = Scheduler.global): SttpBackend[Task, MonixStreams with WebSockets] =
    HttpClientMonixBackend(client, closeClient = false, customizeRequest, customEncodingHandler)(s)

  /**
    * Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports `Observable[ByteBuffer]`
    * streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[Task, MonixStreams with WebSockets] = SttpBackendStub(TaskMonadAsyncError)
}
