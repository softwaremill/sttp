package sttp.client.okhttp

import sttp.client.{Identity, NothingT, SttpBackend}
import sttp.client.testing.{ConvertToFuture, HttpTest}
import sttp.client.{Response, ResponseAs, SttpBackend, _}

class OkHttpSyncHttpTest extends HttpTest[Identity] {
  override implicit val backend: SttpBackend[Identity, Nothing, NothingT] =
    new DigestAuthenticationBackend[Identity, Nothing, NothingT](
      OkHttpSyncBackend()
    )
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id

  "asdasd" in {
    val response = basicRequest
      .get(uri"http://httpbin.org/digest-auth/auth/kasper/test/SHA-512/never")
      .tag(DigestAuthenticationBackend.DigestAuthTag, DigestAuthenticationBackend.DigestAuthData("kasper", "test"))
      .send()
    response.code.code shouldBe 200
  }
}
