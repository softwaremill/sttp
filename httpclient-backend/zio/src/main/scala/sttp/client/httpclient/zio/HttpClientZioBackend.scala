package sttp.client.httpclient.zio

import java.net.http.{HttpClient, HttpRequest}

import sttp.client.httpclient.HttpClientBackend.EncodingHandler
import sttp.client.httpclient.{HttpClientAsyncBackend, HttpClientBackend}
import sttp.client.impl.zio.RIOMonadAsyncError
import sttp.client.{FollowRedirectsBackend, SttpBackend, SttpBackendOptions}
import zio._

class HttpClientZioBackend private (
    client: HttpClient,
    closeClient: Boolean,
    customizeRequest: HttpRequest => HttpRequest,
    customEncodingHandler: EncodingHandler
) extends HttpClientAsyncBackend[Task, Nothing](
      client,
      new RIOMonadAsyncError,
      closeClient,
      customizeRequest,
      customEncodingHandler
    )

object HttpClientZioBackend {
  private def apply(
      client: HttpClient,
      closeClient: Boolean,
      customizeRequest: HttpRequest => HttpRequest,
      customEncodingHandler: EncodingHandler
  ): SttpBackend[Task, Nothing, Nothing] =
    new FollowRedirectsBackend[Task, Nothing, Nothing](
      new HttpClientZioBackend(client, closeClient, customizeRequest, customEncodingHandler)
    )

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  ): Task[SttpBackend[Task, Nothing, Nothing]] =
    Task.effect(
      HttpClientZioBackend(
        HttpClientBackend.defaultClient(options),
        closeClient = true,
        customizeRequest,
        customEncodingHandler
      )
    )

  def managed(
      options: SttpBackendOptions = SttpBackendOptions.Default,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  ): TaskManaged[SttpBackend[Task, Nothing, Nothing]] =
    ZManaged.make(apply(options, customizeRequest, customEncodingHandler))(_.close().ignore)

  def usingClient(
      client: HttpClient,
      customizeRequest: HttpRequest => HttpRequest = identity,
      customEncodingHandler: EncodingHandler = PartialFunction.empty
  ): SttpBackend[Task, Nothing, Nothing] =
    HttpClientZioBackend(client, closeClient = false, customizeRequest, customEncodingHandler)
}
