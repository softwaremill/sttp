package com.softwaremill.sttp.testing

import com.softwaremill.sttp._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}

class SttpBackendStubTests extends FlatSpec with Matchers with ScalaFutures {
  val testingStub = SttpBackendStub(HttpURLConnectionBackend())
    .whenRequestMatches(_.uri.path.startsWith(List("a", "b")))
    .thenRespondOk()
    .whenRequestMatches(_.uri.paramsMap.get("p").contains("v"))
    .thenRespond(10)
    .whenRequestMatches(_.method == Method.GET)
    .thenRespondServerError()
    .whenRequestMatchesPartial({
      case r if r.method == Method.POST && r.uri.path.endsWith(List("partial10")) => Response(Right(10), 200, Nil, Nil)
      case r if r.method == Method.POST && r.uri.path.endsWith(List("partialAda")) => Response(Right("Ada"), 200, Nil, Nil)
    })

  "backend stub" should "use the first rule if it matches" in {
    implicit val b = testingStub
    val r = sttp.get(uri"http://example.org/a/b/c").send()
    r.is200 should be(true)
    r.body should be('left)
  }

  it should "use subsequent rules if the first doesn't match" in {
    implicit val b = testingStub
    val r = sttp
      .get(uri"http://example.org/d?p=v")
      .response(asString.map(_.toInt))
      .send()
    r.body should be(Right(10))
  }

  it should "use the first specified rule if multiple match" in {
    implicit val b = testingStub
    val r = sttp.get(uri"http://example.org/a/b/c?p=v").send()
    r.is200 should be(true)
    r.body should be('left)
  }

  it should "use the default response if no rule matches" in {
    implicit val b = testingStub
    val r = sttp.put(uri"http://example.org/d").send()
    r.code should be(404)
  }

  it should "wrap responses in the desired monad" in {
    import scala.concurrent.ExecutionContext.Implicits.global
    implicit val b = SttpBackendStub(new FutureMonad())
    val r = sttp.post(uri"http://example.org").send()
    r.futureValue.code should be(404)
  }

  it should "use rules in partial function" in {
    implicit val s = testingStub
    val r = sttp.post(uri"http://example.org/partial10").send()
    r.is200 should be(true)
    r.body should be(Right(10))

    val ada = sttp.post(uri"http://example.org/partialAda").send()
    ada.is200 should be(true)
    ada.body should be(Right("Ada"))
  }

  val testingStubWithFallback = SttpBackendStub
    .withFallback(testingStub)
    .whenRequestMatches(_.uri.path.startsWith(List("c")))
    .thenRespond("ok")

  "backend stub with fallback" should "use the stub when response for a request is defined" in {
    implicit val b = testingStubWithFallback

    val r = sttp.post(uri"http://example.org/c").send()
    r.body should be(Right("ok"))
  }

  it should "delegate to the fallback for unhandled requests" in {
    implicit val b = testingStubWithFallback

    val r = sttp.post(uri"http://example.org/a/b").send()
    r.is200 should be(true)
  }
}
