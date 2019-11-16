package sttp.client.httpclient.monix

import java.io.InputStream
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.{HttpClient, HttpRequest}
import java.nio.ByteBuffer

import cats.effect.Resource
import monix.eval.Task
import monix.execution.Scheduler
import monix.reactive.Observable
import sttp.client.httpclient.{HttpClientAsyncBackend, HttpClientBackend, WebSocketHandler}
import sttp.client.impl.monix.TaskMonadAsyncError
import sttp.client.{SttpBackend, _}

import scala.util.{Success, Try}

class HttpClientMonixBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest
)(implicit s: Scheduler)
    extends HttpClientAsyncBackend[Task, Observable[ByteBuffer]](
      client,
      TaskMonadAsyncError,
      closeClient,
      customizeRequest
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
  private def apply(client: HttpClient, closeClient: Boolean, customizeRequest: HttpRequest => HttpRequest)(
      implicit s: Scheduler
  ): SttpBackend[Task, Observable[ByteBuffer], WebSocketHandler] =
    new FollowRedirectsBackend(new HttpClientMonixBackend(client, closeClient, customizeRequest)(s))

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity
  )(
      implicit s: Scheduler = Scheduler.Implicits.global
  ): Task[SttpBackend[Task, Observable[ByteBuffer], WebSocketHandler]] =
    Task.eval(
      HttpClientMonixBackend(HttpClientBackend.defaultClient(options), closeClient = true, customizeRequest)(s)
    )

  def resource(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity
  )(
      implicit s: Scheduler = Scheduler.Implicits.global
  ): Resource[Task, SttpBackend[Task, Observable[ByteBuffer], WebSocketHandler]] =
    Resource.make(apply(options, customizeRequest))(_.close())

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity
  )(implicit s: Scheduler = Scheduler.Implicits.global): SttpBackend[Task, Observable[ByteBuffer], WebSocketHandler] =
    HttpClientMonixBackend(client, closeClient = false, customizeRequest)(s)
}
