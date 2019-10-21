package sttp.client.httpclient

import java.net.http.HttpClient

import sttp.client.testing.{ConvertToFuture, HttpTest}
import sttp.client.{Identity, NothingT, SttpBackend}

class HttpClientSyncHttpTest extends HttpTest[Identity] {

  override implicit val backend: SttpBackend[Identity, Nothing, NothingT] = new HttpClientSyncBackend(
    HttpClient.newHttpClient()
  )
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id
}
