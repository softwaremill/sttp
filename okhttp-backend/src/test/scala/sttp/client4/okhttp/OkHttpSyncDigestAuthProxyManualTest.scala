package sttp.client4.okhttp

import org.scalatest.Ignore
import sttp.client4._
import sttp.client4.testing.{ConvertToFuture, ToFutureWrapper}
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.wrappers.DigestAuthenticationBackend
import sttp.shared.Identity

@Ignore
class OkHttpSyncDigestAuthProxyManualTest extends AsyncFreeSpec with Matchers with ToFutureWrapper {
  val backend: WebSocketBackend[Identity] =
    DigestAuthenticationBackend(OkHttpSyncBackend(options = BackendOptions.httpProxy("localhost", 3128)))

  implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id

  "complex proxy auth with digest" in {
    val response = basicRequest
      .get(uri"http://httpbin.org/digest-auth/auth/andrzej/test/SHA-512")
      .auth
      .digest("andrzej", "test")
      .proxyAuth
      .digest("kasper", "qweqwe")
      .send(backend)
    response.code.code shouldBe 200
  }
}
