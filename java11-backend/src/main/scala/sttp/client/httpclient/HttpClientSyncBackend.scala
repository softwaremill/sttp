package sttp.client.httpclient

import java.net.http.HttpClient
import java.net.http.HttpResponse.BodyHandlers

import sttp.client
import sttp.client.monad.{IdMonad, MonadError}
import sttp.client.{FollowRedirectsBackend, Identity, Request, Response, SttpBackend, SttpBackendOptions}
import sttp.client.ws.WebSocketResponse

class HttpClientSyncBackend(client: HttpClient) extends HttpClientBackend[Identity, Nothing](client) {
  override def send[T](request: Request[T, Nothing]): Identity[Response[T]] = {
    val jRequest = convertRequest(request)
    val response = client.send(jRequest, BodyHandlers.ofByteArray())
    readResponse(response, request.response)
  }

  override def responseMonad: MonadError[Identity] = IdMonad
}

object HttpSyncBackend {
  private def apply(client: HttpClient): SttpBackend[Identity, Nothing, WebSocketResponse] =
    new FollowRedirectsBackend[Identity, Nothing, WebSocketResponse](new HttpClientSyncBackend(client))

  def apply(
      options: SttpBackendOptions = SttpBackendOptions.Default
  ): SttpBackend[Identity, Nothing, WebSocketResponse] =
    HttpSyncBackend(HttpBackend.defaultClient(client.DefaultReadTimeout.toMillis, options))

  def usingClient(client: HttpClient): SttpBackend[Identity, Nothing, WebSocketResponse] =
    HttpSyncBackend(client)
}
