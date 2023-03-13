package sttp.client4

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.httpclient.HttpClientSyncBackend
import sttp.client4.testing.HttpTest.endpoint
import sttp.client4.wrappers.TryBackend

import scala.util.Try

class TryBackendExceptionsTest extends AnyFlatSpec with Matchers {
  val backend: Backend[Try] = TryBackend(HttpClientSyncBackend())

  it should "handle response mapping exceptions" in {
    val error = new IllegalStateException("boom")

    basicRequest
      .post(uri"$endpoint/echo")
      .body("123")
      .response(asStringAlways.map[Int](_ => throw error))
      .send(backend)
      .failed
      .get shouldBe a[IllegalStateException]
  }

  it should "handle connection exceptions" in {
    val req = basicRequest
      .get(uri"http://no-such-domain-1234.com")
      .response(asString)

    req.send(backend).failed.get shouldBe a[SttpClientException.ConnectException]
  }

  it should "handle read exceptions" in {
    val req = basicRequest
      .get(uri"$endpoint/error")
      .response(asString)

    req.send(backend).failed.get shouldBe a[SttpClientException.ReadException]
  }
}
