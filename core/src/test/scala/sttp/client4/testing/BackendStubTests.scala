package sttp.client4.testing

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4._
import sttp.client4.ResponseException.DeserializationException
import sttp.client4.SttpClientException.ReadException
import sttp.client4.internal._
import sttp.client4.ws.async._
import sttp.client4.ws.stream._
import sttp.model._
import sttp.monad.FutureMonad
import sttp.monad.IdentityMonad
import sttp.monad.MonadError
import sttp.monad.TryMonad
import sttp.shared.Identity
import sttp.ws.WebSocketFrame
import sttp.ws.testing.WebSocketStub

import java.io.ByteArrayInputStream
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.util.Failure
import scala.util.Success
import scala.util.Try

class BackendStubTests extends AnyFlatSpec with Matchers with ScalaFutures {
  private val testingStub = SyncBackendStub
    .whenRequestMatches(_.uri.path.startsWith(List("a", "b")))
    .thenRespondOk()
    .whenRequestMatches(_.uri.paramsMap.get("p").contains("v"))
    .thenRespondAdjust("10")
    .whenRequestMatches(_.method == Method.GET)
    .thenRespondServerError()
    .whenRequestMatchesPartial {
      case r if r.method == Method.POST && r.uri.path.endsWith(List("partial10")) =>
        ResponseStub.adjust("10", StatusCode.Ok)
      case r if r.method == Method.POST && r.uri.path.endsWith(List("partialAda")) =>
        ResponseStub.adjust("Ada", StatusCode.Ok)
    }
    .whenRequestMatches(_.uri.port.exists(_ == 8080))
    .thenRespondF(ResponseStub.adjust("OK from monad", StatusCode.Ok))
    .whenRequestMatches(_.uri.port.exists(_ == 8081))
    .thenRespondF(r =>
      ResponseStub.adjust(
        s"OK from request. Request was sent to host: ${r.uri.host.getOrElse("?")}",
        StatusCode.Ok
      )
    )
    .whenRequestMatches(r => r.uri.path.contains("metadata") && r.method == Method.POST)
    .thenRespondOk()
    .whenRequestMatches(r => r.uri.path.contains("metadata") && r.method == Method.PUT)
    .thenRespondServerError()

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
      .response(asString.mapRight((_: String).toInt))
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

  it should "use throw an exception if no rule matches" in {
    val backend = testingStub
    assertThrows[IllegalArgumentException] {
      basicRequest.put(uri"http://example.org/d").send(backend)
    }
  }

  it should "wrap exceptions in the desired monad" in {
    val backend: Backend[Try] = BackendStub(TryMonad)
    val r = basicRequest.post(uri"http://example.org").send(backend)
    r match {
      case Failure(_: IllegalArgumentException) => succeed
      case _                                    => fail(s"Should be a failure: $r")
    }
  }

  it should "adjust exceptions so they are wrapped with SttpClientException" in {
    val testingBackend = SyncBackendStub.whenAnyRequest.thenRespondAdjust("{}", StatusCode(200))

    val request = () =>
      basicRequest
        .get(uri"./test")
        .response(
          asString.mapWithMetadata((_, meta) => throw DeserializationException("", new RuntimeException("test"), meta))
        )
        .send(testingBackend)

    val readException = the[SttpClientException.ReadException] thrownBy request()
    readException.cause shouldBe a[DeserializationException]
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
    val backend = SyncBackendStub
      .whenRequestMatches(_ => true)
      .thenRespond(throw new TimeoutException())

    a[ReadException] should be thrownBy {
      basicRequest.get(uri"http://example.org").send(backend)
    }
  }

  it should "handle exceptions thrown using .thenThrow" in {
    val backend = SyncBackendStub
      .whenRequestMatches(_ => true)
      .thenThrow(new TimeoutException())

    a[ReadException] should be thrownBy {
      basicRequest.get(uri"http://example.org").send(backend)
    }
  }

  it should "try to convert a basic response to a mapped one" in {
    val backend = SyncBackendStub
      .whenRequestMatches(_ => true)
      .thenRespondAdjust("10")

    val result = basicRequest
      .get(uri"http://example.org")
      .mapResponseRight((_: String).toInt)
      .mapResponseRight((_: Int) * 2)
      .send(backend)

    result.body should be(Right(20))
  }

  it should "handle a 201 as a success" in {
    val backend = SyncBackendStub.whenAnyRequest
      .thenRespondWithCode(StatusCode.Created)

    val result = basicRequest
      .get(uri"http://example.org")
      .send(backend)

    result.body should be(Right(""))
  }

  it should "handle a 300 as a failure" in {
    val backend = SyncBackendStub.whenAnyRequest
      .thenRespondWithCode(StatusCode.MultipleChoices)

    val result = basicRequest
      .get(uri"http://example.org")
      .send(backend)

    result.body should be(Left(""))
  }

  it should "handle a 400 as a failure" in {
    val backend = SyncBackendStub.whenAnyRequest
      .thenRespondWithCode(StatusCode.BadRequest)

    val result = basicRequest
      .get(uri"http://example.org")
      .send(backend)

    result.code should be(StatusCode.BadRequest)
    result.body should be(Left("Bad Request"))
  }

  it should "handle a 500 as a failure" in {
    val backend = SyncBackendStub.whenAnyRequest
      .thenRespondWithCode(StatusCode.InternalServerError)

    val result = basicRequest
      .get(uri"http://example.org")
      .send(backend)

    result.code should be(StatusCode.InternalServerError)
    result.body should be(Left("Internal Server Error"))
  }

  it should "not hold the calling thread when passed a future monad" in {
    val LongTime = 10.seconds
    val LongTimeMillis = LongTime.toMillis
    val before = System.currentTimeMillis()

    val backend = BackendStub(new FutureMonad()).whenAnyRequest
      .thenRespondF(Platform.delayedFuture(LongTime) {
        ResponseStub.adjust("OK", StatusCode.Ok)
      })

    basicRequest
      .get(uri"http://example.org")
      .send(backend)

    val after = System.currentTimeMillis()

    (after - before) should be < LongTimeMillis
  }

  it should "serve consecutive raw responses" in {
    val backend = SyncBackendStub.whenAnyRequest
      .thenRespondCyclic(ResponseStub.adjust("first"), ResponseStub.adjust("second"), ResponseStub.adjust("third"))

    basicRequest.get(uri"http://example.org").send(backend).body should be(Right("first"))
    basicRequest.get(uri"http://example.org").send(backend).body should be(Right("second"))
    basicRequest.get(uri"http://example.org").send(backend).body should be(Right("third"))
    basicRequest.get(uri"http://example.org").send(backend).body should be(Right("first"))
  }

  it should "serve consecutive responses" in {
    val backend = SyncBackendStub.whenAnyRequest
      .thenRespondCyclic(
        ResponseStub.adjust("first"),
        ResponseStub.adjust("error", StatusCode.InternalServerError)
      )

    def testResult = basicRequest.get(uri"http://example.org").send(backend)

    testResult.is200 should be(true)
    testResult.isServerError should be(true)
    testResult.is200 should be(true)
  }

  it should "always return a string when requested to do so" in {
    val backend: SyncBackend = BackendStub.synchronous.whenAnyRequest
      .thenRespondServerError()

    basicRequest
      .get(uri"http://example.org")
      .response(asStringAlways)
      .send(backend)
      .body shouldBe "Internal Server Error"
  }

  it should "return both responses when requested to do so" in {
    val backend = BackendStub.synchronous.whenAnyRequest.thenRespondAdjust("1234")
    basicRequest
      .get(uri"http://example.org")
      .response(asBoth(asString.mapRight((_: String).toInt), asStringAlways))
      .send(backend)
      .body shouldBe ((Right(1234), "1234"))
  }

  it should "return a web socket, given a stub, for an unsafe websocket-always request" in {
    val backend = WebSocketBackendStub.synchronous.whenAnyRequest
      .thenRespondAdjust(WebSocketStub.initialReceive(List(WebSocketFrame.text("hello"))))

    val ws = basicRequest
      .get(uri"ws://example.org")
      .response(asWebSocketAlwaysUnsafe[Identity])
      .send(backend)
      .body

    ws.receive() shouldBe WebSocketFrame.text("hello")
  }

  it should "return a web socket, given a stub, for a safe websocket-always request" in {
    val backend: WebSocketSyncBackend = WebSocketBackendStub.synchronous.whenAnyRequest
      .thenRespondAdjust(WebSocketStub.initialReceive(List(WebSocketFrame.text("hello"))))

    val frame = basicRequest
      .get(uri"ws://example.org")
      .response(asWebSocketAlways[Identity, WebSocketFrame](ws => ws.receive()))
      .send(backend)
      .body

    frame shouldBe WebSocketFrame.text("hello")
  }

  it should "return a web socket, given a web socket, for a safe websocket-always request" in {
    val backend: WebSocketSyncBackend = WebSocketBackendStub.synchronous.whenAnyRequest
      .thenRespondAdjust(WebSocketStub.initialReceive(List(WebSocketFrame.text("hello"))).build(IdentityMonad))

    val frame = basicRequest
      .get(uri"ws://example.org")
      .response(asWebSocketAlways[Identity, WebSocketFrame](ws => ws.receive()))
      .send(backend)
      .body

    frame shouldBe WebSocketFrame.text("hello")
  }

  it should "return a web socket, given a web socket, for a safe websocket request" in {
    val backend: WebSocketSyncBackend = WebSocketBackendStub.synchronous.whenAnyRequest
      .thenRespondAdjust(
        WebSocketStub.initialReceive(List(WebSocketFrame.text("hello"))).build(IdentityMonad),
        if (TestPlatform.Current == TestPlatform.JS) StatusCode.Ok else StatusCode.SwitchingProtocols
      )

    val frame = basicRequest
      .get(uri"ws://example.org")
      .response(asWebSocket[Identity, WebSocketFrame](ws => ws.receive()))
      .send(backend)
      .body

    frame shouldBe Right(WebSocketFrame.text("hello"))
  }

  it should "return a web socket, given a web socket, for a safe websocket request using the Try monad" in {
    val backend: WebSocketBackend[Try] = WebSocketBackendStub(TryMonad).whenAnyRequest
      .thenRespondAdjust(
        WebSocketStub.initialReceive(List(WebSocketFrame.text("hello"))).build(TryMonad),
        if (TestPlatform.Current == TestPlatform.JS) StatusCode.Ok else StatusCode.SwitchingProtocols
      )

    val frame = basicRequest
      .get(uri"ws://example.org")
      .response(asWebSocket[Try, WebSocketFrame](ws => ws.receive()))
      .send(backend)
      .map(_.body)

    frame shouldBe Success(Right(WebSocketFrame.text("hello")))
  }

  it should "return a stream, given a stream, for a unsafe stream request" in {
    val backend: StreamBackend[Identity, TestStreams] =
      StreamBackendStub.synchronous[TestStreams].whenAnyRequest.thenRespondAdjust(List(1: Byte))

    val result = basicRequest
      .get(uri"http://example.org")
      .response(asStreamUnsafe(TestStreams))
      .send(backend)
      .body

    result shouldBe Right(List(1: Byte))
  }

  it should "return a stream, given a stream, for a safe stream request" in {
    val backend: StreamBackend[Identity, TestStreams] =
      StreamBackendStub.synchronous[TestStreams].whenAnyRequest.thenRespondAdjust(List(1: Byte))

    val result = basicRequest
      .get(uri"http://example.org")
      .response(asStream[Identity, Byte, TestStreams](TestStreams)(l => l.head))
      .send(backend)
      .body

    result shouldBe Right(1: Byte)
  }

  it should "return a stream, given a stream, for a safe stream request using the Try monad" in {
    val backend: StreamBackend[Try, TestStreams] =
      StreamBackendStub[Try, TestStreams](TryMonad).whenAnyRequest.thenRespondAdjust(List(1: Byte))

    val result = basicRequest
      .get(uri"http://example.org")
      .response(asStream[Try, Byte, TestStreams](TestStreams)(l => Success(l.head)))
      .send(backend)
      .map(_.body)

    result shouldBe Success(Right(1: Byte))
  }

  it should "run the web socket pipe, for a web socket stream request" in {
    var capturedFrame: WebSocketFrame = null

    val backend: WebSocketStreamBackend[Identity, TestStreams] =
      WebSocketStreamBackendStub
        .synchronous[TestStreams]
        .whenAnyRequest
        .thenRespondAdjust(
          WebSocketStreamConsumer[Identity](TestStreams) { pipe =>
            capturedFrame = pipe(WebSocketFrame.text("hello"))
          },
          if (TestPlatform.Current == TestPlatform.JS) StatusCode.Ok else StatusCode.SwitchingProtocols
        )

    // running for side-effects
    val _ = basicRequest
      .get(uri"http://example.org")
      .response(asWebSocketStream(TestStreams) {
        case WebSocketFrame.Text(p, _, _) => WebSocketFrame.text(s"echo: $p")
        case f                            => f
      })
      .send(backend)

    capturedFrame shouldBe WebSocketFrame.text("echo: hello")
  }

  it should "evaluate side effects on each request" in {
    // given
    type Lazy[T] = () => T
    object LazyMonad extends MonadError[Lazy] {
      override def unit[T](t: T): Lazy[T] = () => t
      override def map[T, T2](fa: Lazy[T])(f: T => T2): Lazy[T2] = () => f(fa())
      override def flatMap[T, T2](fa: Lazy[T])(f: T => Lazy[T2]): Lazy[T2] = () => f(fa())()
      override def error[T](t: Throwable): Lazy[T] = () => throw t
      override protected def handleWrappedError[T](rt: Lazy[T])(h: PartialFunction[Throwable, Lazy[T]]): Lazy[T] =
        () =>
          try rt()
          catch { case e if h.isDefinedAt(e) => h(e)() }
      override def ensure[T](f: Lazy[T], e: => Lazy[Unit]): Lazy[T] = () =>
        try f()
        finally e()
    }

    val counter = new AtomicInteger(0)
    val backend: Backend[Lazy] = BackendStub(LazyMonad).whenRequestMatchesPartial { case _ =>
      counter.getAndIncrement()
      ResponseStub.adjust("ok")
    }

    // creating the "send effect" once ...
    val result = basicRequest.get(uri"http://example.org").send(backend)

    // when
    // ... and then using it twice
    result().body shouldBe Right("ok")
    result().body shouldBe Right("ok")

    // then
    counter.get() shouldBe 2
  }

  private val testingStubWithFallback = SyncBackendStub
    .withFallback(testingStub)
    .whenRequestMatches(_.uri.path.startsWith(List("c")))
    .thenRespondAdjust("ok")

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

  "backend stub" should "preserve request metadata" in {
    val uri = uri"http://test/metadata"
    val request = basicRequest.post(uri)
    val r = request.send(testingStub)
    val metadata = r.request
    metadata.method should be(Method.POST)
    metadata.uri should be(uri)
    metadata.toString() should be(request.onlyMetadata.toString())
    r.is200 should be(true)
    r.body should be(Right(""))
  }

  "backend stub" should "preserve request metadata for failed request" in {
    val uri = uri"http://test-2/metadata"
    val request = basicRequest.put(uri)
    val r = request.send(testingStub)
    val metadata = r.request
    metadata.method should be(Method.PUT)
    metadata.uri should be(uri)
    metadata.toString() should be(request.onlyMetadata.toString())
    r.isServerError should be(true)
  }

  private val s = "Hello, world!"
  private val adjustTestData = List[(Any, ResponseAs[_], Any)](
    (s, sttp.client4.ignore, Some(())),
    (s, asString(Utf8), Some(Right(s))),
    (s.getBytes(Utf8), asString(Utf8), Some(Right(s))),
    (new ByteArrayInputStream(s.getBytes(Utf8)), asString(Utf8), Some(Right(s))),
    (10, asString(Utf8), None),
    ("10", asString(Utf8).mapRight((_: String).toInt), Some(Right(10))),
    (11, asString(Utf8).mapRight((_: String).toInt), None),
    ((), asString(Utf8), Some(Right("")))
  )

  for {
    (body, responseAs, expectedResult) <- adjustTestData
  }
    it should s"adjust $body to $expectedResult when specified as $responseAs" in {
      val backend = SyncBackendStub.whenAnyRequest.thenRespondAdjust(body)
      val request = basicRequest.get(uri"http://example.org").response(responseAs)

      Try(request.send(backend).body).toOption should be(expectedResult)
    }
}
