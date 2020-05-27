package sttp.client.httpclient.monix

import java.io.InputStream
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.{HttpClient, HttpRequest}
import java.nio.ByteBuffer

import cats.effect.Resource
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import sttp.client.httpclient.HttpClientBackend.EncodingHandler
import sttp.client.httpclient.{HttpClientAsyncBackend, HttpClientBackend, WebSocketHandler}
import sttp.client.impl.monix.TaskMonadAsyncError
import sttp.client.testing.SttpBackendStub
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}

import scala.util.{Success, Try}

class HttpClientMonixBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: EncodingHandler
)(implicit s: Scheduler)
    extends HttpClientAsyncBackend[Task, Observable[ByteBuffer]](
      client,
      TaskMonadAsyncError,
      closeClient,
      customizeRequest,
      customEncodingHandler
    ) {
  override def streamToRequestBody(stream: Observable[ByteBuffer]): HttpRequest.BodyPublisher = {
    BodyPublishers.fromPublisher(new ReactivePublisherJavaAdapter[ByteBuffer](stream.toReactivePublisher))
  }

  override def responseBodyToStream(responseBody: InputStream): Try[Observable[ByteBuffer]] = {
    Success(
      Observable
        .fromInputStream(Task.now(responseBody))
        .map(ByteBuffer.wrap)
        .guaranteeCase(_ => Task(responseBody.close()))
    )
  }
}

object HttpClientMonixBackend {
  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      customEncodingHandler: EncodingHandler
  )(implicit
      s: Scheduler
  ): SttpBackend[Task, Observable[ByteBuffer], WebSocketHandler] =
    new FollowRedirectsBackend(
      new HttpClientMonixBackend(client, closeClient, customizeRequest, customEncodingHandler)(s)
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  )(implicit
      s: Scheduler = Scheduler.global
  ): Task[SttpBackend[Task, Observable[ByteBuffer], WebSocketHandler]] =
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
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  )(implicit
      s: Scheduler = Scheduler.global
  ): Resource[Task, SttpBackend[Task, Observable[ByteBuffer], WebSocketHandler]] =
    Resource.make(apply(options, customizeRequest, customEncodingHandler))(_.close())

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  )(implicit s: Scheduler = Scheduler.global): SttpBackend[Task, Observable[ByteBuffer], WebSocketHandler] =
    HttpClientMonixBackend(client, closeClient = false, customizeRequest, customEncodingHandler)(s)

  /**
    * Create a stub backend for testing, which uses the [[Task]] response wrapper, and supports `Observable[ByteBuffer]`
    * streaming.
    *
    * See [[SttpBackendStub]] for details on how to configure stub responses.
    */
  def stub: SttpBackendStub[Task, Observable[ByteBuffer]] = SttpBackendStub(TaskMonadAsyncError)
}
