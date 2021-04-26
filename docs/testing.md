# Testing

If you need a stub backend for use in tests instead of a "real" backend (you probably don't want to make HTTP calls during unit tests), you can use the `SttpBackendStub` class. It allows specifying how the backend should respond to requests matching given predicates.

You can also create a stub backend using [akka-http routes](backends/akka.md).

## Creating a stub backend

An empty backend stub can be created using the following ways:

* by calling `.stub` on the "real" base backend's companion object, e.g. `AsyncHttpClientZioBackend.stub` or `HttpClientMonixBackend.stub`
* by using one of the factory methods `SttpBackendStub.synchronous` or `SttpBackendStub.asynchronousFuture`, which return stubs which use the `Identity` or standard Scala's `Future` effects without streaming support
* by explicitly specifying the effect and supported capabilities, e.g. `SttpBackendStub[Task, MonixStreams with WebSockets](TaskMonad)`
* by specifying a fallback/delegate backend, see below

Some code which will be reused among following examples:

```scala mdoc
import sttp.client3._
import sttp.model._
import sttp.client3.testing._
import java.io.File
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global 

case class User(id: String)
``` 

## Specifying behavior

Behavior of the stub can be specified using a series of invocations of the `whenRequestMatches` and `thenRespond` methods:

```scala mdoc:compile-only
val testingBackend = SttpBackendStub.synchronous
  .whenRequestMatches(_.uri.path.startsWith(List("a", "b")))
  .thenRespond("Hello there!")
  .whenRequestMatches(_.method == Method.POST)
  .thenRespondServerError()

val response1 = basicRequest.get(uri"http://example.org/a/b/c").send(testingBackend)
// response1.body will be Right("Hello there")

val response2 = basicRequest.post(uri"http://example.org/d/e").send(testingBackend)
// response2.code will be 500
```

It is also possible to match requests by partial function, returning a response. E.g.:

```scala mdoc:compile-only
val testingBackend = SttpBackendStub.synchronous
  .whenRequestMatchesPartial({
    case r if r.uri.path.endsWith(List("partial10")) =>
      Response("Not found", StatusCode.NotFound)

    case r if r.uri.path.endsWith(List("partialAda")) =>
      // additional verification of the request is possible
      assert(r.body == StringBody("z", "utf-8"))
      Response.ok("Ada")
  })

val response1 = basicRequest.get(uri"http://example.org/partial10").send(testingBackend)
// response1.body will be Right(10)

val response2 = basicRequest.post(uri"http://example.org/partialAda").send(testingBackend)
// response2.body will be Right("Ada")
```

```eval_rst
.. note::

  This approach to testing has one caveat: the responses are not type-safe. That is, the stub backend cannot match on or verify that the type of the response body matches the response body type, as it was requested. However, when a "raw" response is provided (a ``String``, ``Array[Byte]``, ``InputStream``, or a non-blocking stream wrapped in ``RawStream``), it will be handled as specified by the response specification - see below for details.
```

Another way to specify the behaviour is passing response wrapped in the effect to the stub. It is useful if you need to test a scenario with a slow server, when the response should be not returned immediately, but after some time. Example with Futures:

```scala mdoc:compile-only
val testingBackend = SttpBackendStub.asynchronousFuture
  .whenAnyRequest
  .thenRespondF(Future {
    Thread.sleep(5000)
    Response.ok(Right("OK"))
  })

val responseFuture = basicRequest.get(uri"http://example.org").send(testingBackend)
// responseFuture will complete after 5 seconds with "OK" response
```

The returned response may also depend on the request: 

```scala mdoc:compile-only
val testingBackend = SttpBackendStub.synchronous
  .whenAnyRequest
  .thenRespondF(req =>
    Response.ok(Right(s"OK, got request sent to ${req.uri.host}"))
  )

val response = basicRequest.get(uri"http://example.org").send(testingBackend)
// response.body will be Right("OK, got request sent to example.org")
```

You can define consecutive raw responses that will be served:

```scala mdoc:compile-only
val testingBackend: SttpBackendStub[Identity, Any] = SttpBackendStub.synchronous
  .whenAnyRequest
  .thenRespondCyclic("first", "second", "third")

basicRequest.get(uri"http://example.org").send(testingBackend)       // Right("OK, first")
basicRequest.get(uri"http://example.org").send(testingBackend)       // Right("OK, second")
basicRequest.get(uri"http://example.org").send(testingBackend)       // Right("OK, third")
basicRequest.get(uri"http://example.org").send(testingBackend)       // Right("OK, first")
```

Or multiple `Response` instances:

```scala mdoc:compile-only
val testingBackend: SttpBackendStub[Identity, Any] = SttpBackendStub.synchronous
  .whenAnyRequest
  .thenRespondCyclicResponses(
    Response.ok[String]("first"),
    Response("error", StatusCode.InternalServerError, "Something went wrong")
  )

basicRequest.get(uri"http://example.org").send(testingBackend)       // code will be 200
basicRequest.get(uri"http://example.org").send(testingBackend)       // code will be 500
basicRequest.get(uri"http://example.org").send(testingBackend)       // code will be 200
```

The `sttp.client3.testing` package also contains a utility method to force the body as a string (`forceBodyAsString`) or as a byte array (`forceBodyAsByteArray`), if the body is not a stream or multipart:

```scala mdoc:compile-only
val testingBackend = SttpBackendStub.synchronous
  .whenRequestMatches(_.forceBodyAsString.contains("Hello, world!"))
  .thenRespond("Hello back!")
```

If the stub is given a request, for which no behavior is stubbed, it will return a failed effect with an `IllegalArgumentException`. 

## Simulating exceptions

If you want to simulate an exception being thrown by a backend, e.g. a socket timeout exception, you can do so by throwing the appropriate exception instead of the response, e.g.:

```scala mdoc:compile-only
val testingBackend = SttpBackendStub.synchronous
  .whenRequestMatches(_ => true)
  .thenRespond(throw new SttpClientException.ConnectException(
    basicRequest.get(uri"http://example.com"), new RuntimeException))
```

## Adjusting the response body type

The stub will attempt to convert the body returned by the stub (as specified using the `.whenXxx` methods) to the desired type. If the given body isn't in one of the supported "raw" types, no conversions are done and the value is returned as-is. This might be useful when:

* testing code which maps a basic response body to a custom type, e.g. mapping a raw json string using a decoder to a domain type
* reading a classpath resource (which results in an `InputStream`) and requesting a response of e.g. type `String`
* using resource-safe response specifications for streaming and websockets

The following conversions are supported:

* anything to `()` (unit), when the response is ignored
* `InputStream` and `Array[Byte]` to `String`
* `InputStream` and `String` to `Array[Byte]`
* `WebSocketStub` to `WebSocket` 
* `WebSocketStub` and `WebSocket` are supplied to the websocket-consuming functions, if the response specification describes such interactions
* `SttpBackendStub.RawStream` is always treated as a raw stream value, and returned when the response should be returned as a stream or consumed using the provided function
* any of the above to custom types through mapped response specifications 

## Example: returning JSON

For example, if you want to return a JSON response, simply use `.withResponse(String)` as below:

```scala mdoc:compile-only
val testingBackend = SttpBackendStub.synchronous
  .whenRequestMatches(_ => true)
  .thenRespond(""" {"username": "john", "age": 65 } """)

def parseUserJson(a: Array[Byte]): User = ???

val response = basicRequest.get(uri"http://example.com")
  .response(asByteArrayAlways.map(parseUserJson))
  .send(testingBackend)
```                                                                  

In the example above, the stub's rules specify that a response with a `String`-body should be returned for any request; the request, on the other hand, specifies that response body should be parsed from a byte array to a custom `User` type. These type don't match, so the `SttpBackendStub` will in this case convert the body to the desired type.

## Example: returning a file

If you want to save the response to a file and have the response handler set up like this:

```scala mdoc:compile-only
val destination = new File("path/to/file.ext")
basicRequest.get(uri"http://example.com").response(asFile(destination))
```

With the stub created as follows:

```scala mdoc:compile-only
val fileResponseHandle = new File("path/to/file.ext")
SttpBackendStub.synchronous
  .whenRequestMatches(_ => true)
  .thenRespond(fileResponseHandle)
```

the `File` set up in the stub will be returned as though it was the `File` set up as `destination` in the response handler above. This means that the file from `fileResponseHandle` is not written to `destination`.

If you actually want a file to be written you can set up the stub like this:

```scala mdoc:compile-only
import org.apache.commons.io.FileUtils
import cats.effect._
import sttp.client3.impl.cats.implicits._
import sttp.monad.MonadAsyncError

val sourceFile = new File("path/to/file.ext")
val destinationFile = new File("path/to/file.ext")
SttpBackendStub(implicitly[MonadAsyncError[IO]])
  .whenRequestMatches(_ => true)
  .thenRespondF { _ =>
    FileUtils.copyFile(sourceFile, destinationFile)
    IO(Response(Right(destinationFile), StatusCode.Ok, ""))
  }
```

## Delegating to another backend

It is also possible to create a stub backend which delegates calls to another (possibly "real") backend if none of the specified predicates match a request. This can be useful during development, to partially stub a yet incomplete API with which we integrate:

```scala mdoc:compile-only
val testingBackend =
  SttpBackendStub.withFallback(HttpURLConnectionBackend())
    .whenRequestMatches(_.uri.path.startsWith(List("a")))
    .thenRespond("I'm a STUB!")

val response1 = basicRequest.get(uri"http://api.internal/a").send(testingBackend)
// response1.body will be Right("I'm a STUB")

val response2 = basicRequest.post(uri"http://api.internal/b").send(testingBackend)
// response2 will be whatever a "real" network call to api.internal/b returns
```

## Testing streams

Streaming responses can be stubbed the same as ordinary values, with one difference. If the stubbed response contains the raw stream, which should be then transformed as described by the response specification, the stub must know that it handles a raw stream. This can be achieved by wrapping the stubbed stream using `SttpBackendStub.RawStream`.

If the response specification is a resource-safe consumer of the stream, the function will only be invoked if the body is a `RawStream` (with the contained value).

Otherwise, the stub can be also configured to return the high-level (already mapped/transformed) response body.

## Testing web sockets

Like streams, web sockets can be stubbed as ordinary values, by providing `WebSocket` or `WebSocketStub` instances.

If the response specification is a resource-safe consumer of the web socket, the function will be invoked if the provided stubbed body is a `WebSocket` or `WebSocketStub`.

The stub can be configured to return the high-level (already mapped/transformed) response body.

### WebSocketStub

`WebSocketStub` allows easy creation of stub `WebSocket` instances. Such instances wrap a state machine that can be used
to simulate simple WebSocket interactions. The user sets initial responses for `receive` calls as well as logic to add
further messages in reaction to `send` calls. 

For example:

```scala mdoc:compile-only
import sttp.ws.testing.WebSocketStub
import sttp.ws.WebSocketFrame

val backend = SttpBackendStub.synchronous
val webSocketStub = WebSocketStub
  .initialReceive(
    List(WebSocketFrame.text("Hello from the server!"))
  )
  .thenRespondS(0) {
    case (counter, tf: WebSocketFrame.Text) => (counter + 1, List(WebSocketFrame.text(s"echo: ${tf.payload}")))
    case (counter, _)                       => (counter, List.empty)
  }

backend.whenAnyRequest.thenRespond(webSocketStub)
```

There is a possiblity to add error responses as well. If this is not enough, using a custom implementation of 
the `WebSocket` trait is recommended.

## Verifying, that a request was sent

Using `RecordingSttpBackend` it's possible to capture all interactions in which a backend has been involved.

The recording backend is a [backend wrapper](backends/wrappers/custom.md), and it can wrap any backend, but it's most
useful when combine witht the backend stub. 

Example usage:

```scala mdoc:compile-only
import scala.util.Try

val testingBackend = new RecordingSttpBackend(
  SttpBackendStub.synchronous
    .whenRequestMatches(_.uri.path.startsWith(List("a", "b")))
    .thenRespond("Hello there!")
)

val response1 = basicRequest.get(uri"http://example.org/a/b/c").send(testingBackend)
// response1.body will be Right("Hello there")

testingBackend.allInteractions: List[(Request[_, _], Try[Response[_]])]
// the list will contain one element and can be verified in a test 
```