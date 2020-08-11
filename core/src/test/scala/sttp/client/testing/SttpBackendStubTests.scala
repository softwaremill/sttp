package sttp.client.testing

import java.io.ByteArrayInputStream
import java.util.concurrent.TimeoutException

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client._
import sttp.client.internal._
import sttp.client.monad.{FutureMonad, IdMonad, TryMonad}
import sttp.model._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Success, Try}

class SttpBackendStubTests extends AnyFlatSpec with Matchers with ScalaFutures {
  private val testingStub = SttpBackendStub[Identity, Any](IdMonad)
    .whenRequestMatches(_.uri.path.startsWith(List("a", "b")))
    .thenRespondOk()
    .whenRequestMatches(_.uri.paramsMap.get("p").contains("v"))
    .thenRespond("10")
    .whenRequestMatches(_.method == Method.GET)
    .thenRespondServerError()
    .whenRequestMatchesPartial({
      case r if r.method == Method.POST && r.uri.path.endsWith(List("partial10")) =>
        Response(Right("10"), StatusCode.Ok, "OK", Nil, Nil)
      case r if r.method == Method.POST && r.uri.path.endsWith(List("partialAda")) =>
        Response(Right("Ada"), StatusCode.Ok, "OK", Nil, Nil)
    })
    .whenRequestMatches(_.uri.port.exists(_ == 8080))
    .thenRespondWrapped(Response(Right("OK from monad"), StatusCode.Ok, "OK", Nil, Nil))
    .whenRequestMatches(_.uri.port.exists(_ == 8081))
    .thenRespondWrapped(r =>
      Response(Right(s"OK from request. Request was sent to host: ${r.uri.host}"), StatusCode.Ok, "OK", Nil, Nil)
    )

  "backend stub" should "use the first rule if it matches" in {
    val backend = testingStub
    val r = basicRequest.get(uri"http://example.org/a/b/c").send(backend)
    r.is200 should be(true)
    r.body should be(Right(""))
  }

  it should "use subsequent rules if the first doesn't match" in {
    val backend = testingStub
    val r = basicRequest
      .get(uri"http://example.org/d?p=v")
      .response(asString.mapRight(_.toInt))
      .send(backend)
    r.body should be(Right(10))
  }

  it should "use the first specified rule if multiple match" in {
    val backend = testingStub
    val r = basicRequest.get(uri"http://example.org/a/b/c?p=v").send(backend)
    r.is200 should be(true)
    r.body should be(Right(""))
  }

  it should "respond with monad with set response" in {
    val backend = testingStub
    val r = basicRequest.post(uri"http://example.org:8080").send(backend)
    r.is200 should be(true)
    r.body should be(Right("OK from monad"))
  }

  it should "respond with monad with response created from request" in {
    val backend = testingStub
    val r = basicRequest.post(uri"http://example.org:8081").send(backend)
    r.is200 should be(true)
    r.body should be(Right("OK from request. Request was sent to host: example.org"))
  }

  it should "use the default response if no rule matches" in {
    val backend = testingStub
    val r = basicRequest.put(uri"http://example.org/d").send(backend)
    r.code shouldBe StatusCode.NotFound
    r.body.isLeft shouldBe true
    r.body.left.get should startWith("Not Found")
  }

  it should "wrap responses in the desired monad" in {
    val backend = SttpBackendStub[Try, Any](TryMonad)
    val r = basicRequest.post(uri"http://example.org").send(backend)
    r.map(_.code) shouldBe Success(StatusCode.NotFound)
  }

  it should "use rules in partial function" in {
    val backend = testingStub
    val r = basicRequest.post(uri"http://example.org/partial10").send(backend)
    r.is200 should be(true)
    r.body should be(Right("10"))

    val ada = basicRequest.post(uri"http://example.org/partialAda").send(backend)
    ada.is200 should be(true)
    ada.body should be(Right("Ada"))
  }

  it should "handle exceptions thrown instead of a response (synchronous)" in {
    val backend = SttpBackendStub[Identity, Any](IdMonad)
      .whenRequestMatches(_ => true)
      .thenRespond(throw new TimeoutException())

    a[TimeoutException] should be thrownBy {
      basicRequest.get(uri"http://example.org").send(backend)
    }
  }

  it should "handle exceptions thrown instead of a response (asynchronous)" in {
    val backend: SttpBackendStub[Future, Any] = SttpBackendStub(new FutureMonad())
      .whenRequestMatches(_ => true)
      .thenRespond(throw new TimeoutException())

    val result = basicRequest.get(uri"http://example.org").send(backend)
    result.failed.map(_ shouldBe a[TimeoutException])
  }

  it should "try to convert a basic response to a mapped one" in {
    val backend = SttpBackendStub[Identity, Any](IdMonad)
      .whenRequestMatches(_ => true)
      .thenRespond("10")

    val result = basicRequest
      .get(uri"http://example.org")
      .mapResponseRight(_.toInt)
      .mapResponseRight(_ * 2)
      .send(backend)

    result.body should be(Right(20))
  }

  it should "handle a 201 as a success" in {
    val backend = SttpBackendStub[Identity, Any](IdMonad).whenAnyRequest
      .thenRespondWithCode(StatusCode.Created)

    val result = basicRequest
      .get(uri"http://example.org")
      .send(backend)

    result.body should be(Right(""))
  }

  it should "handle a 300 as a failure" in {
    val backend = SttpBackendStub[Identity, Any](IdMonad).whenAnyRequest
      .thenRespondWithCode(StatusCode.MultipleChoices)

    val result = basicRequest
      .get(uri"http://example.org")
      .send(backend)

    result.body should be(Left(""))
  }

  it should "handle a 400 as a failure" in {
    val backend = SttpBackendStub[Identity, Any](IdMonad).whenAnyRequest
      .thenRespondWithCode(StatusCode.BadRequest)

    val result = basicRequest
      .get(uri"http://example.org")
      .send(backend)

    result.body should be(Left(""))
  }

  it should "handle a 500 as a failure" in {
    val backend = SttpBackendStub[Identity, Any](IdMonad).whenAnyRequest
      .thenRespondWithCode(StatusCode.InternalServerError)

    val result = basicRequest
      .get(uri"http://example.org")
      .send(backend)

    result.body should be(Left(""))
  }

  it should "not hold the calling thread when passed a future monad" in {
    val LongTime = 10.seconds
    val LongTimeMillis = LongTime.toMillis
    val before = System.currentTimeMillis()

    val backend: SttpBackendStub[Future, Any] = SttpBackendStub(new FutureMonad()).whenAnyRequest
      .thenRespondWrapped(Platform.delayedFuture(LongTime) {
        Response(Right("OK"), StatusCode.Ok, "", Nil, Nil)
      })

    basicRequest
      .get(uri"http://example.org")
      .send(backend)

    val after = System.currentTimeMillis()

    (after - before) should be < LongTimeMillis
  }

  it should "serve consecutive raw responses" in {
    val backend: SttpBackend[Identity, Any] = SttpBackendStub(IdMonad).whenAnyRequest
      .thenRespondCyclic("first", "second", "third")

    basicRequest.get(uri"http://example.org").send(backend).body should be(Right("first"))
    basicRequest.get(uri"http://example.org").send(backend).body should be(Right("second"))
    basicRequest.get(uri"http://example.org").send(backend).body should be(Right("third"))
    basicRequest.get(uri"http://example.org").send(backend).body should be(Right("first"))
  }

  it should "serve consecutive responses" in {
    val backend: SttpBackend[Identity, Any] = SttpBackendStub(IdMonad).whenAnyRequest
      .thenRespondCyclicResponses(
        Response.ok[String]("first"),
        Response("error", StatusCode.InternalServerError, "Something went wrong")
      )

    basicRequest.get(uri"http://example.org").send(backend).is200 should be(true)
    basicRequest.get(uri"http://example.org").send(backend).isServerError should be(true)
    basicRequest.get(uri"http://example.org").send(backend).is200 should be(true)
  }

  it should "always return a string when requested to do so" in {
    val backend: SttpBackend[Identity, Any] = SttpBackendStub(IdMonad).whenAnyRequest
      .thenRespondServerError()

    basicRequest
      .get(uri"http://example.org")
      .response(asStringAlways)
      .send(backend)
      .body shouldBe "Internal server error"
  }

  private val testingStubWithFallback = SttpBackendStub
    .withFallback[Identity, Any, Any](testingStub)
    .whenRequestMatches(_.uri.path.startsWith(List("c")))
    .thenRespond("ok")

  "backend stub with fallback" should "use the stub when response for a request is defined" in {
    val backend = testingStubWithFallback

    val r = basicRequest.post(uri"http://example.org/c").send(backend)
    r.body should be(Right("ok"))
  }

  it should "delegate to the fallback for unhandled requests" in {
    val backend = testingStubWithFallback

    val r = basicRequest.post(uri"http://example.org/a/b").send(backend)
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
      SttpBackendStub.tryAdjustResponseBody(responseAs, body, ResponseMetadata(Nil, StatusCode.Ok, "")) should be(
        expectedResult
      )
    }
  }
}
