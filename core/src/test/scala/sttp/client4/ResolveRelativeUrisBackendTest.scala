package sttp.client4

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.testing.BackendStub
import sttp.client4.wrappers.ResolveRelativeUrisBackend
import sttp.model.StatusCode

class ResolveRelativeUrisBackendTest extends AnyFlatSpec with Matchers {
  it should "not resolve absolute URIs" in {
    // given
    val delegate = BackendStub.synchronous.whenRequestMatchesPartial { case r =>
      Response(r.uri.toString, StatusCode.Ok)
    }
    val backend = ResolveRelativeUrisBackend(delegate, uri"http://example.org")

    // when
    val response = basicRequest.response(asStringAlways).get(uri"http://example2.org/test?a=1").send(backend)

    // then
    response.body shouldBe "http://example2.org/test?a=1"
  }

  it should "resolve relative URIs" in {
    // given
    val delegate = BackendStub.synchronous.whenRequestMatchesPartial { case r =>
      Response(r.uri.toString, StatusCode.Ok)
    }
    val backend = wrappers.ResolveRelativeUrisBackend(delegate, uri"http://example.org")

    // when
    val response = basicRequest.response(asStringAlways).get(uri"/test?a=1").send(backend)

    // then
    response.body shouldBe "http://example.org/test?a=1"
  }
}
