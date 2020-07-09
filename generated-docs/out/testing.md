# Testing

If you need a stub backend for use in tests instead of a "real" backend (you probably don't want to make HTTP calls during unit tests), you can use the `SttpBackendStub` class. It allows specifying how the backend should respond to requests matching given predicates.

You can also create a stub backend using [akka-http routes](backends/akka.md).

## Creating a stub backend

An empty backend stub can be created using the following ways:

* by calling `.stub` on the "real" base backend's companion object, e.g. `AsyncHttpClientZioBackend.stub` or `HttpClientMonixBackend.stub`
* by using one of the factory methods `SttpBackendStub.synchronous` or `SttpBackendStub.asynchronousFuture`, which return stubs which use the `Identity` or standard Scala's `Future` response wrappers without streaming support
* by explicitly giving the response wrapper monad and supported streams type, e.g. `SttpBackendStub[Task, Observable[ByteBuffer]](TaskMonad)`
* by specifying a fallback/delegate backend, see below

Some code which will be reused among following examples:
```scala
import sttp.client._
import sttp.model._
import sttp.client.testing._
import java.io.File
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global  

case class User(id: String)
``` 

## Specifying behavior

Behavior of the stub can be specified using a combination of the `whenRequestMatches` and `thenRespond` methods:

```scala
implicit val testingBackend = SttpBackendStub.synchronous
  .whenRequestMatches(_.uri.path.startsWith(List("a", "b")))
  .thenRespond("Hello there!")
  .whenRequestMatches(_.method == Method.POST)
  .thenRespondServerError()

val response1 = basicRequest.get(uri"http://example.org/a/b/c").send()
// response1.body will be Right("Hello there")

val response2 = basicRequest.post(uri"http://example.org/d/e").send()
```

It is also possible to match requests by partial function, returning a response. E.g.:

```scala
implicit val testingBackend = SttpBackendStub.synchronous
  .whenRequestMatchesPartial({
    case r if r.uri.path.endsWith(List("partial10")) =>
      Response("Not found", StatusCode.NotFound)

    case r if r.uri.path.endsWith(List("partialAda")) =>
      // additional verification of the request is possible
      assert(r.body == StringBody("z", "utf-8"))
      Response.ok("Ada")
  })

val response1 = basicRequest.get(uri"http://example.org/partial10").send()
// response1.body will be Right(10)

val response2 = basicRequest.post(uri"http://example.org/partialAda").send()
```

This approach to testing has one caveat: the responses are not type-safe. That is, the stub backend cannot match on or verify that the type of the response body matches the response body type requested.

Another way to specify the behaviour is passing response wrapped in the result monad to the stub. It is useful if you need to test a scenario with a slow server, when the response should be not returned immediately, but after some time. Example with Futures:

```scala
implicit val testingBackend = SttpBackendStub.asynchronousFuture
  .whenAnyRequest
  .thenRespondWrapped(Future {
    Thread.sleep(5000)
    Response(Right("OK"), StatusCode.Ok, "", Nil, Nil)
  })

val responseFuture = basicRequest.get(uri"http://example.org").send()
```

The returned response may also depend on the request: 

```scala
implicit val testingBackend = SttpBackendStub.synchronous
  .whenAnyRequest
  .thenRespondWrapped(req =>
    Response(Right(s"OK, got request sent to ${req.uri.host}"), StatusCode.Ok, "", Nil, Nil)
  )

val response = basicRequest.get(uri"http://example.org").send()
```

You can define consecutive raw responses that will be served:

```scala
implicit val testingBackend:SttpBackendStub[Identity, Nothing, NothingT] = SttpBackendStub.synchronous
  .whenAnyRequest
  .thenRespondCyclic("first", "second", "third")

basicRequest.get(uri"http://example.org").send()       // Right("OK, first")       // Right("OK, first")
basicRequest.get(uri"http://example.org").send()       // Right("OK, second")       // Right("OK, second")
basicRequest.get(uri"http://example.org").send()       // Right("OK, third")       // Right("OK, third")
basicRequest.get(uri"http://example.org").send()       // Right("OK, first")
```

Or multiple `Response` instances:

```scala
implicit val testingBackend:SttpBackendStub[Identity, Nothing, NothingT] = SttpBackendStub.synchronous
  .whenAnyRequest
  .thenRespondCyclicResponses(
    Response.ok[String]("first"),
    Response("error", StatusCode.InternalServerError, "Something went wrong")
  )

basicRequest.get(uri"http://example.org").send()       // code will be 200       // code will be 200
basicRequest.get(uri"http://example.org").send()       // code will be 500       // code will be 500
basicRequest.get(uri"http://example.org").send()       // code will be 200
```

## Simulating exceptions

If you want to simulate an exception being thrown by a backend, e.g. a socket timeout exception, you can do so by throwing the appropriate exception instead of the response, e.g.:

```scala
implicit val testingBackend = SttpBackendStub.synchronous
  .whenRequestMatches(_ => true)
  .thenRespond(throw new SttpClientException.ConnectException(new RuntimeException))
```

## Adjusting the response body type

If the type of the response body returned by the stub's rules (as specified using the `.whenXxx` methods) doesn't match what was specified in the request, the stub will attempt to convert the body to the desired type. This might be useful when:

* testing code which maps a basic response body to a custom type, e.g. mapping a raw json string using a decoder to a domain type
* reading a classpath resource (which results in an `InputStream`) and requesting a response of e.g. type `String`

The following conversions are supported:

* anything to `()` (unit), when the response is ignored
* `InputStream` and `Array[Byte]` to `String`
* `InputStream` and `String` to `Array[Byte]`
* `InputStream`, `String` and `Array[Byte]` to custom types through mapped response specifications

## Example: returning JSON

For example, if you want to return a JSON response, simply use `.withResponse(String)` as below:

```scala
implicit val testingBackend = SttpBackendStub.synchronous
  .whenRequestMatches(_ => true)
  .thenRespond(""" {"username": "john", "age": 65 } """)

def parseUserJson(a: Array[Byte]): User = ???

val response = basicRequest.get(uri"http://example.com")
  .response(asByteArrayAlways.map(parseUserJson))
  .send()
```                                                                  

In the example above, the stub's rules specify that a response with a `String`-body should be returned for any request; the request, on the other hand, specifies that response body should be parsed from a byte array to a custom `User` type. These type don't match, so the `SttpBackendStub` will in this case convert the body to the desired type.

Note that no conversions will be attempted for streaming response bodies.

## Example: returning a file

If you want to return a file and have a response handler set up like this:

```scala
val destination = new File("path/to/file.ext")
basicRequest.get(uri"http://example.com").response(asFile(destination))
```

Then set up the mock like this:

```scala
val fileResponseHandle = new File("path/to/file.ext")
SttpBackendStub.synchronous
  .whenRequestMatches(_ => true)
  .thenRespond(fileResponseHandle)
```

the `File` set up in the stub will be returned as though it was the `File` set up as `destination` in the response handler above. This means that the file from `fileResponseHandle` is not written to `destination`.

If you actually want a file to be written you can set up the stub like this:

```scala
import org.apache.commons.io.FileUtils
import cats.effect.IO
import sttp.client.impl.cats.implicits._
import sttp.client.monad.MonadError

val sourceFile = new File("path/to/file.ext")
val destinationFile = new File("path/to/file.ext")
SttpBackendStub(implicitly[MonadError[IO]])
  .whenRequestMatches(_ => true)
  .thenRespondWrapped { _ =>
    FileUtils.copyFile(sourceFile, destinationFile)
    IO(Response(Right(destinationFile), StatusCode.Ok, ""))
  }
```

## Delegating to another backend

It is also possible to create a stub backend which delegates calls to another (possibly "real") backend if none of the specified predicates match a request. This can be useful during development, to partially stub a yet incomplete API with which we integrate:

```scala
implicit val testingBackend =
  SttpBackendStub.withFallback[Identity,Nothing,Nothing,NothingT](HttpURLConnectionBackend())
    .whenRequestMatches(_.uri.path.startsWith(List("a")))
    .thenRespond("I'm a STUB!")

val response1 = basicRequest.get(uri"http://api.internal/a").send()
// response1.body will be Right("I'm a STUB")

val response2 = basicRequest.post(uri"http://api.internal/b").send()
```

## Testing WebSockets

Stub methods `whenRequestMatches` and `whenAnyRequest` also allow specifying behavior when `openWebsocket` is called,
using `thenRespondWebSocket` or `thenHandleOpenWebSocket`. 

The first behavior, `thenRespondWebSocket` is best suited when the [high-level](websockets.md) websocket interface
is used, i.e. when the client code manipulates `WebSocket[F]` to implement the websocket-handling logic. 

In this case, the handler that is passed to the `openWebsocket` call is ignored, and the test double `WebSocket`
implementation is returned.

For example:

```scala
import sttp.client.ws._
import zio.{App => _, _}
import sttp.client.asynchttpclient.zio._

val testWebSocket: WebSocket[Task] = ???

implicit val testingBackend =
  AsyncHttpClientZioBackend.stub
    .whenAnyRequest
    .thenRespondWebSocket(testWebSocket)

for {
  handler <- ZioWebSocketHandler() // using sttp's "high-level" websockets
  openResponse <- testingBackend.openWebsocket(basicRequest.get(uri"wss://some.uri"), handler)
  webSocket = openResponse.result // interaction with WebSocket is interesting in this case
  message <- webSocket.receive
} yield message
```

### WebSocketStub

`WebSocketStub` allows easy creation of stub `WebSocket` instances. Such instances wrap a state machine that can be used
to simulate simple WebSocket interactions. The user sets initial responses for `receive` calls as well as logic to add
further messages in reaction to `send` calls. `SttpBackendStub` has a special API that accepts a `WebSocketStub` and
builds a `WebSocket` instance out of it.

For example:

```scala
import sttp.model.ws._

val backend = SttpBackendStub.synchronous[Identity]
val webSocketStub = WebSocketStub
  .withInitialIncoming(
    List(WebSocketFrame.text("Hello from the server!"))
  )
  .thenRespondS(0) {
    case (counter, tf: WebSocketFrame.Text) => (counter + 1, List(WebSocketFrame.text(s"echo: ${tf.payload}")))
    case (counter, _)                       => (counter, List.empty)
  }

backend.whenAnyRequest.thenRespondWebSocket(webSocketStub)
```

There is a possiblity to add error responses as well. If this is not enough, using a custom implementation of 
the `WebSocket` trait is recommended.

### Using the client-provided handler when testing WebSockets

[Akka backend](backends/akka.md) works a bit differently in regard to WebSockets. In this case the user defines
a `Flow[Message, Message, Mat]` to define WebSocket client behavior.

The `thenHandleOpenWebSocket` method allows specifying stub behavior, which uses the given client-flow to simulate 
client-server interaction in tests.
 
When the websocket behavior is captured by the handler (as is the case when using Akka), we can use the value passed by 
the user to couple it with simulated server-side behavior, and run the whole interaction. 

For example:

```scala
import akka.stream.scaladsl._
import akka.Done
import akka.http.scaladsl.model.ws._
import akka.stream._
import akka.actor.ActorSystem
import sttp.client.akkahttp._
import scala.concurrent.Await
import scala.concurrent.duration._

implicit val system = ActorSystem("sttp-ws-test")
implicit val materializer = ActorMaterializer()

// it should say Hi! and 42
val behaviorToTest: Flow[Message, Message, Future[Done]] = ???
// setup test double
var received = List.empty[String]
val testFlow: Flow[Message, Message, Future[Done]] => Future[Done] = clientFlow => {
  val ((outQueue, flowCompleted), inQueue) = Source
    .queue(1, OverflowStrategy.fail)
    .viaMat(clientFlow)(Keep.both)
    .toMat(Sink.queue())(Keep.both)
    .run()

  def recordInboundMessage: Future[Unit] =
    inQueue.pull().flatMap {
      case None      => recordInboundMessage
      case Some(msg) => Future.successful { received = msg.asTextMessage.getStrictText :: received }
    }

  (for {
    _ <- recordInboundMessage
    _ <- outQueue.offer(TextMessage("Give me a number"))
    _ <- recordInboundMessage
    _ = outQueue.complete()
    _ <- outQueue.watchCompletion()
  } yield ()).flatMap(_ => flowCompleted)
}

implicit val backend = AkkaHttpBackend.stub.whenAnyRequest
  .thenHandleOpenWebSocket(testFlow)

// code under test with test doubles
Await.ready(backend
  .openWebsocket(basicRequest.get(uri"wss://echo.websocket.org"), behaviorToTest)
  .flatMap(_.result), Duration.Inf)
// assertions can be performed
assert(received.reverse == List("Hi!", "42"))
```
