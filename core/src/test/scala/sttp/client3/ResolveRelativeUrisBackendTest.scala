package sttp.client3

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3.testing.{RecordingSttpBackend, SttpBackendStub}

class ResolveRelativeUrisBackendTest extends AnyFlatSpec with Matchers {
  it should "not resolve absolute URIs" in {
    // given
    val delegate = new RecordingSttpBackend(SttpBackendStub.synchronous.whenAnyRequest.thenRespondOk())
    val backend = ResolveRelativeUrisBackend(delegate, uri"http://example.org")

    // when
    basicRequest.get(uri"http://example2.org/test?a=1").send(backend)

    // then
    delegate.allInteractions should have size (1)
    delegate.allInteractions.head._1.uri.toString shouldBe "http://example2.org/test?a=1"
  }

  it should "resolve relative URIs" in {
    // given
    val delegate = new RecordingSttpBackend(SttpBackendStub.synchronous.whenAnyRequest.thenRespondOk())
    val backend = ResolveRelativeUrisBackend(delegate, uri"http://example.org")

    // when
    basicRequest.get(uri"/test?a=1").send(backend)

    // then
    delegate.allInteractions should have size (1)
    delegate.allInteractions.head._1.uri.toString shouldBe "http://example.org/test?a=1"
  }
}
