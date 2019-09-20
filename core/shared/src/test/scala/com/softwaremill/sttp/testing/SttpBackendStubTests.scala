package com.softwaremill.sttp.testing

import java.io.ByteArrayInputStream
import java.util.concurrent.TimeoutException

import com.github.ghik.silencer.silent
import com.softwaremill.sttp._
import com.softwaremill.sttp.internal._
import com.softwaremill.sttp.model._
import com.softwaremill.sttp.monad.{FutureMonad, IdMonad}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

@silent("dead code")
class SttpBackendStubTests extends FlatSpec with Matchers with ScalaFutures {
  private val testingStub = SttpBackendStub(IdMonad)
    .whenRequestMatches(_.uri.path.startsWith(List("a", "b")))
    .thenRespondOk()
    .whenRequestMatches(_.uri.paramsMap.get("p").contains("v"))
    .thenRespond("10")
    .whenRequestMatches(_.method == Method.GET)
    .thenRespondServerError()
    .whenRequestMatchesPartial({
      case r if r.method == Method.POST && r.uri.path.endsWith(List("partial10")) =>
        Response(Right("10"), 200, "OK", Nil, Nil)
      case r if r.method == Method.POST && r.uri.path.endsWith(List("partialAda")) =>
        Response(Right("Ada"), 200, "OK", Nil, Nil)
    })
    .whenRequestMatches(_.uri.port.exists(_ == 8080))
    .thenRespondWrapped(Response(Right("OK from monad"), 200, "OK", Nil, Nil))
    .whenRequestMatches(_.uri.port.exists(_ == 8081))
    .thenRespondWrapped(
      r => Response(Right(s"OK from request. Request was sent to host: ${r.uri.host}"), 200, "OK", Nil, Nil)
    )

  "backend stub" should "use the first rule if it matches" in {
    implicit val b = testingStub
    val r = sttp.get(uri"http://example.org/a/b/c").send()
    r.is200 should be(true)
    r.body should be(Right(""))
  }

  it should "use subsequent rules if the first doesn't match" in {
    implicit val b = testingStub
    val r = sttp
      .get(uri"http://example.org/d?p=v")
      .response(asString.mapRight(_.toInt))
      .send()
    r.body should be(Right(10))
  }

  it should "use the first specified rule if multiple match" in {
    implicit val b = testingStub
    val r = sttp.get(uri"http://example.org/a/b/c?p=v").send()
    r.is200 should be(true)
    r.body should be(Right(""))
  }

  it should "respond with monad with set response" in {
    implicit val b = testingStub
    val r = sttp.post(uri"http://example.org:8080").send()
    r.is200 should be(true)
    r.body should be(Right("OK from monad"))
  }

  it should "respond with monad with response created from request" in {
    implicit val b = testingStub
    val r = sttp.post(uri"http://example.org:8081").send()
    r.is200 should be(true)
    r.body should be(Right("OK from request. Request was sent to host: example.org"))
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
    r.body should be(Right("10"))

    val ada = sttp.post(uri"http://example.org/partialAda").send()
    ada.is200 should be(true)
    ada.body should be(Right("Ada"))
  }

  it should "handle exceptions thrown instead of a response (synchronous)" in {
    implicit val s = SttpBackendStub(IdMonad)
      .whenRequestMatches(_ => true)
      .thenRespond(throw new TimeoutException())

    a[TimeoutException] should be thrownBy {
      sttp.get(uri"http://example.org").send()
    }
  }

  it should "handle exceptions thrown instead of a response (asynchronous)" in {
    implicit val s = SttpBackendStub(new FutureMonad())
      .whenRequestMatches(_ => true)
      .thenRespond(throw new TimeoutException())

    val result = sttp.get(uri"http://example.org").send()
    result.failed.futureValue shouldBe a[TimeoutException]
  }

  it should "try to convert a basic response to a mapped one" in {
    implicit val s = SttpBackendStub(IdMonad)
      .whenRequestMatches(_ => true)
      .thenRespond("10")

    val result = sttp
      .get(uri"http://example.org")
      .mapResponseRight(_.toInt)
      .mapResponseRight(_ * 2)
      .send()

    result.body should be(Right(20))
  }

  it should "handle a 201 as a success" in {
    implicit val s = SttpBackendStub(IdMonad).whenAnyRequest
      .thenRespondWithCode(201)

    val result = sttp
      .get(uri"http://example.org")
      .send()

    result.body should be(Right(""))

  }

  it should "handle a 300 as a failure" in {
    implicit val s = SttpBackendStub(IdMonad).whenAnyRequest
      .thenRespondWithCode(300)

    val result = sttp
      .get(uri"http://example.org")
      .send()

    result.body should be(Left(""))

  }

  it should "handle a 400 as a failure" in {
    implicit val s = SttpBackendStub(IdMonad).whenAnyRequest
      .thenRespondWithCode(400)

    val result = sttp
      .get(uri"http://example.org")
      .send()

    result.body should be(Left(""))

  }

  it should "handle a 500 as a failure" in {
    implicit val s = SttpBackendStub(IdMonad).whenAnyRequest
      .thenRespondWithCode(500)

    val result = sttp
      .get(uri"http://example.org")
      .send()

    result.body should be(Left(""))

  }

  it should "not hold the calling thread when passed a future monad" in {
    val LongTime = 10.seconds
    val LongTimeMillis = LongTime.toMillis
    val before = System.currentTimeMillis()

    implicit val s = SttpBackendStub(new FutureMonad()).whenAnyRequest
      .thenRespondWrapped(Platform.delayedFuture(LongTime) {
        Response(Right("OK"), 200, "", Nil, Nil)
      })

    sttp
      .get(uri"http://example.org")
      .send()

    val after = System.currentTimeMillis()

    (after - before) should be < LongTimeMillis
  }

  it should "serve consecutive raw responses" in {
    implicit val s: SttpBackend[Identity, Nothing] = SttpBackendStub(IdMonad).whenAnyRequest
      .thenRespondCyclic("first", "second", "third")

    sttp.get(uri"http://example.org").send().body should be(Right("first"))
    sttp.get(uri"http://example.org").send().body should be(Right("second"))
    sttp.get(uri"http://example.org").send().body should be(Right("third"))
    sttp.get(uri"http://example.org").send().body should be(Right("first"))
  }

  it should "serve consecutive responses" in {
    implicit val s: SttpBackend[Identity, Nothing] = SttpBackendStub(IdMonad).whenAnyRequest
      .thenRespondCyclicResponses(
        Response.ok[String]("first"),
        Response("error", 500, "Something went wrong")
      )

    sttp.get(uri"http://example.org").send().is200 should be(true)
    sttp.get(uri"http://example.org").send().isServerError should be(true)
    sttp.get(uri"http://example.org").send().is200 should be(true)
  }

  private val testingStubWithFallback = SttpBackendStub
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

  private val s = "Hello, world!"
  private val adjustTestData = List[(Any, ResponseAs[_, _], Any)](
    (s, IgnoreResponse, Some(())),
    (s, asString(Utf8), Some(Right(s))),
    (s.getBytes(Utf8), asString(Utf8), Some(Right(s))),
    (new ByteArrayInputStream(s.getBytes(Utf8)), asString(Utf8), Some(Right(s))),
    (10, asString(Utf8), None),
    ("10", asString(Utf8).mapRight(_.toInt), Some(Right(10))),
    (11, asString(Utf8).mapRight(_.toInt), None)
  )

  behavior of "tryAdjustResponseBody"

  for {
    (body, responseAs, expectedResult) <- adjustTestData
  } {
    it should s"adjust $body to $expectedResult when specified as $responseAs" in {
      SttpBackendStub.tryAdjustResponseBody(responseAs, body, ResponseMetadata(Nil, 200, "")) should be(expectedResult)
    }
  }
}
