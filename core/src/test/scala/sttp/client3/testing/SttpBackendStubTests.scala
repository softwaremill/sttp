package sttp.client3.testing

import java.io.ByteArrayInputStream
import java.util.concurrent.TimeoutException

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.{Streams, WebSockets}
import sttp.client3._
import sttp.client3.internal._
import sttp.client3.monad.IdMonad
import sttp.model._
import sttp.monad.{FutureMonad, TryMonad}
import sttp.ws.WebSocketFrame
import sttp.ws.testing.WebSocketStub

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

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
        Response(Right("10"), StatusCode.Ok, "OK")
      case r if r.method == Method.POST && r.uri.path.endsWith(List("partialAda")) =>
        Response(Right("Ada"), StatusCode.Ok, "OK")
    })
    .whenRequestMatches(_.uri.port.exists(_ == 8080))
    .thenRespondF(Response(Right("OK from monad"), StatusCode.Ok, "OK"))
    .whenRequestMatches(_.uri.port.exists(_ == 8081))
    .thenRespondF(r =>
      Response(Right(s"OK from request. Request was sent to host: ${r.uri.host.getOrElse("?")}"), StatusCode.Ok, "OK")
    )

  "backend stub" should "use the first rule if it matches" in {
    val backend = testingStub
    val r = basicRequest.get(uri"http://example.org/a/b/c").send(backend)
    r.is200 should be(true)
    r.body should be(Right("OK"))
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
    r.body should be(Right("OK"))
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

  it should "use throw an exception if no rule matches" in {
    val backend = testingStub
    assertThrows[IllegalArgumentException] {
      basicRequest.put(uri"http://example.org/d").send(backend)
    }
  }

  it should "wrap exceptions in the desired monad" in {
    val backend = SttpBackendStub[Try, Any](TryMonad)
    val r = basicRequest.post(uri"http://example.org").send(backend)
    r match {
      case Failure(_: IllegalArgumentException) => succeed
      case _                                    => fail(s"Should be a failure: $r")
    }
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
      .thenRespondF(Platform.delayedFuture(LongTime) {
        Response(Right("OK"), StatusCode.Ok, "")
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

    def testResult = basicRequest.get(uri"http://example.org").send(backend)

    testResult.is200 should be(true)
    testResult.isServerError should be(true)
    testResult.is200 should be(true)
  }

  it should "always return a string when requested to do so" in {
    val backend: SttpBackend[Identity, Any] = SttpBackendStub.synchronous.whenAnyRequest
      .thenRespondServerError()

    basicRequest
      .get(uri"http://example.org")
      .response(asStringAlways)
      .send(backend)
      .body shouldBe "Internal server error"
  }

  it should "return both responses when requested to do so" in {
    val backend = SttpBackendStub.synchronous.whenAnyRequest.thenRespond("1234")
    basicRequest
      .get(uri"http://example.org")
      .response(asBoth(asString.mapRight(_.toInt), asStringAlways))
      .send(backend)
      .body shouldBe ((Right(1234), "1234"))
  }

  it should "return a web socket, given a stub, for an unsafe websocket-always request" in {
    val backend: SttpBackend[Identity, WebSockets] = SttpBackendStub.synchronous.whenAnyRequest
      .thenRespond(WebSocketStub.initialReceive(List(WebSocketFrame.text("hello"))))

    val ws = basicRequest
      .get(uri"ws://example.org")
      .response(asWebSocketAlwaysUnsafe[Identity])
      .send(backend)
      .body

    ws.receive() shouldBe WebSocketFrame.text("hello")
  }

  it should "return a web socket, given a stub, for a safe websocket-always request" in {
    val backend: SttpBackend[Identity, WebSockets] = SttpBackendStub.synchronous.whenAnyRequest
      .thenRespond(WebSocketStub.initialReceive(List(WebSocketFrame.text("hello"))))

    val frame = basicRequest
      .get(uri"ws://example.org")
      .response(asWebSocketAlways[Identity, WebSocketFrame](ws => ws.receive()))
      .send(backend)
      .body

    frame shouldBe WebSocketFrame.text("hello")
  }

  it should "return a web socket, given a web socket, for a safe websocket-always request" in {
    val backend: SttpBackend[Identity, WebSockets] = SttpBackendStub.synchronous.whenAnyRequest
      .thenRespond(WebSocketStub.initialReceive(List(WebSocketFrame.text("hello"))).build(IdMonad))

    val frame = basicRequest
      .get(uri"ws://example.org")
      .response(asWebSocketAlways[Identity, WebSocketFrame](ws => ws.receive()))
      .send(backend)
      .body

    frame shouldBe WebSocketFrame.text("hello")
  }

  it should "return a web socket, given a web socket, for a safe websocket request" in {
    val backend: SttpBackend[Identity, WebSockets] = SttpBackendStub.synchronous.whenAnyRequest
      .thenRespond(
        WebSocketStub.initialReceive(List(WebSocketFrame.text("hello"))).build(IdMonad),
        StatusCode.SwitchingProtocols
      )

    val frame = basicRequest
      .get(uri"ws://example.org")
      .response(asWebSocket[Identity, WebSocketFrame](ws => ws.receive()))
      .send(backend)
      .body

    frame shouldBe Right(WebSocketFrame.text("hello"))
  }

  it should "return a web socket, given a web socket, for a safe websocket request using the Try monad" in {
    val backend: SttpBackend[Try, WebSockets] = SttpBackendStub[Try, WebSockets](TryMonad).whenAnyRequest
      .thenRespond(
        WebSocketStub.initialReceive(List(WebSocketFrame.text("hello"))).build(TryMonad),
        StatusCode.SwitchingProtocols
      )

    val frame = basicRequest
      .get(uri"ws://example.org")
      .response(asWebSocket[Try, WebSocketFrame](ws => ws.receive()))
      .send(backend)
      .map(_.body)

    frame shouldBe Success(Right(WebSocketFrame.text("hello")))
  }

  trait TestStreams extends Streams[TestStreams] {
    override type BinaryStream = List[Byte]
    override type Pipe[A, B] = A => B
  }
  object TestStreams extends TestStreams

  it should "return a stream, given a stream, for a unsafe stream request" in {
    val backend: SttpBackend[Identity, TestStreams] = SttpBackendStub[Identity, TestStreams](IdMonad).whenAnyRequest
      .thenRespond(SttpBackendStub.RawStream(List(1: Byte)))

    val result = basicRequest
      .get(uri"http://example.org")
      .response(asStreamUnsafe(TestStreams))
      .send(backend)
      .body

    result shouldBe Right(List(1: Byte))
  }

  it should "return a stream, given a stream, for a safe stream request" in {
    val backend: SttpBackend[Identity, TestStreams] = SttpBackendStub[Identity, TestStreams](IdMonad).whenAnyRequest
      .thenRespond(SttpBackendStub.RawStream(List(1: Byte)))

    val result = basicRequest
      .get(uri"http://example.org")
      .response(asStream[Identity, Byte, TestStreams](TestStreams)(l => l.head))
      .send(backend)
      .body

    result shouldBe Right(1: Byte)
  }

  it should "return a stream, given a stream, for a safe stream request using the Try monad" in {
    val backend: SttpBackend[Try, TestStreams] = SttpBackendStub[Try, TestStreams](TryMonad).whenAnyRequest
      .thenRespond(SttpBackendStub.RawStream(List(1: Byte)))

    val result = basicRequest
      .get(uri"http://example.org")
      .response(asStream[Try, Byte, TestStreams](TestStreams)(l => Success(l.head)))
      .send(backend)
      .map(_.body)

    result shouldBe Success(Right(1: Byte))
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
      SttpBackendStub.tryAdjustResponseBody(
        responseAs,
        body,
        ResponseMetadata(StatusCode.Ok, "", Nil)
      )(IdMonad) should be(
        expectedResult
      )
    }
  }
}
