# The stub backend

If you need a stub backend for use in tests instead of a "real" backend (you probably don't want to make HTTP calls during unit tests), you can use the `BackendStub` class. It allows specifying how the backend should respond to requests matching given predicates.

The [pekko-http](../backends/pekko.md) or [akka-http](../backends/akka.md) backends also provide an alternative way to create a stub, from a request-transforming function.

## Creating a stub backend

An empty backend stub can be created using the following ways:

* by calling `.stub` on the "real" base backend's companion object, e.g. `DefaultSyncBackend.stub`, `HttpClientZioBackend.stub`, `HttpClientMonixBackend.stub`
* by using one of the factory methods `BackendStub.synchronous` or `BackendStub.asynchronousFuture`, which return stubs which use the `Identity` or standard Scala's `Future` effects without streaming support
* by explicitly specifying the effect and supported capabilities:
  * for cats-effect `BackendStub[IO](implicitly[MonadAsyncError[IO]])`
  * for ZIO `BackendStub[Task](new RIOMonadAsyncError[Any])`
  * for Monix `BackendStub[Task](TaskMonad)`
* by instantiating backend stubs which support streaming or WebSockets, mirroring the hierarchy of the base backends:
  * `StreamBackendStub`, e.g. `StreamBackendStub[IO, Fs2Streams[IO]](implicitly[MonadAsyncError[IO]])` (for cats-effect with fs2)
  * `WebSocketBackendStub`, e.g. `WebSocketBackendStub[Task](new RIOMonadAsyncError[Any])` (for ZIO)
  * `WebSocketStreamBackendStub`
  * `WebSocketSyncBackendStub`
* by specifying a fallback/delegate backend, see below

Responses used in the stubbing are most conveniently created using factory methods from `ResponseStub`.

Some code which will be reused among following examples:

```scala mdoc
import sttp.client4.*
import sttp.model.*
import sttp.client4.testing.*
import java.io.File
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

case class User(id: String)
```

## Specifying behavior

Behavior of the stub can be specified using a series of invocations of the `whenRequestMatches` and `thenRespond...` methods:

```scala mdoc:compile-only
val testingBackend = SyncBackendStub
  .whenRequestMatches(_.uri.path.startsWith(List("a", "b")))
  .thenRespondAdjust("Hello there!")
  .whenRequestMatches(_.method == Method.POST)
  .thenRespondServerError()

val response1 = basicRequest.get(uri"http://example.org/a/b/c").send(testingBackend)
// response1.body will be Right("Hello there")

val response2 = basicRequest.post(uri"http://example.org/d/e").send(testingBackend)
// response2.code will be 500
```

It is also possible to match requests by partial function, returning a response. E.g.:

```scala mdoc:compile-only
val testingBackend = SyncBackendStub
  .whenRequestMatchesPartial({
    case r if r.uri.path.endsWith(List("partial10")) =>
      ResponseStub.adjust("Not found", StatusCode.NotFound)

    case r if r.uri.path.endsWith(List("partialAda")) =>
      // additional verification of the request is possible
      assert(r.body == StringBody("z", "utf-8"))
      ResponseStub.adjust("Ada")
  })

val response1 = basicRequest.get(uri"http://example.org/partial10").send(testingBackend)
// response1.body will be Right(10)

val response2 = basicRequest.post(uri"http://example.org/partialAda").send(testingBackend)
// response2.body will be Right("Ada")
```

```{note}
This approach to testing has one caveat: the responses are not type-safe. That is, the stub backend cannot match on or verify that the type of the response body matches the response body type, as it was requested. However, the response bodies can be adjusted, and attempted to be handled as specified by the response description. Hence, you can provide bodies as a `String`, `Array[Byte]`, `InputStream`, `File`, `WebSocket`, `WebSocketStub`, or a non-blocking binary stream, and the conversions to the desired type will happen as part of the test.
```

Another way to specify the behavior is passing response wrapped in the effect to the stub. It is useful if you need to test a scenario with a slow server, when the response should be not returned immediately, but after some time. Example with Futures:

```scala mdoc:compile-only
val testingBackend = BackendStub.asynchronousFuture
  .whenAnyRequest
  .thenRespondF(Future {
    Thread.sleep(5000)
    ResponseStub.adjust("OK")
  })

val responseFuture = basicRequest.get(uri"http://example.org").send(testingBackend)
// responseFuture will complete after 5 seconds with "OK" response
```

The returned response may also depend on the request:

```scala mdoc:compile-only
val testingBackend = SyncBackendStub
  .whenAnyRequest
  .thenRespondF(req =>
    ResponseStub.adjust(s"OK, got request sent to ${req.uri.host}")
  )

val response = basicRequest.get(uri"http://example.org").send(testingBackend)
// response.body will be Right("OK, got request sent to example.org")
```

You can define consecutive raw responses that will be served:

```scala mdoc:compile-only
val testingBackend: SyncBackendStub = SyncBackendStub
  .whenAnyRequest
  .thenRespondCyclic(
    ResponseStub.adjust("first"),
    ResponseStub.adjust("error", StatusCode.InternalServerError)
  )

basicRequest.get(uri"http://example.org").send(testingBackend)       // code will be 200
basicRequest.get(uri"http://example.org").send(testingBackend)       // code will be 500
basicRequest.get(uri"http://example.org").send(testingBackend)       // code will be 200
```

The `sttp.client4.testing` package also contains a utility method to force the body as a string (`forceBodyAsString`) or as a byte array (`forceBodyAsByteArray`), if the body is not a stream or multipart:

```scala mdoc:compile-only
val testingBackend = SyncBackendStub
  .whenRequestMatches(_.forceBodyAsString.contains("Hello, world!"))
  .thenRespondAdjust("Hello back!")
```

If the stub is given a request, for which no behavior is stubbed, it will return a failed effect with an `IllegalArgumentException`.

## Simulating exceptions

If you want to simulate an exception being thrown by a backend, e.g. a socket timeout exception, you can do so by using the `thenThrow` method:

```scala mdoc:compile-only
val testingBackend = SyncBackendStub
  .whenRequestMatches(_ => true)
  .thenThrow(new SttpClientException.ConnectException(
    basicRequest.get(uri"http://example.com"), new RuntimeException))
```

## Adjusting the response body type

When using `.thenRespondAdjust` or `ResponseStub.adjust` methods, the stub will attempt to convert the body returned by the stub to the desired type. If the given body isn't in one of the supported "raw" types, an `IllegalArgumentException` will be thrown. This is to:

* test code which maps a basic response body to a custom type, e.g. mapping a raw json string using a decoder to a domain type
* reading a classpath resource (which results in an `InputStream`) and requesting a response of e.g. type `String`
* using resource-safe response specifications for streaming and websockets

The following conversions are supported:

* anything to `()` (unit), when the response is ignored
* `InputStream` and `Array[Byte]` to `String`
* `InputStream` and `String` to `Array[Byte]`
* `WebSocketStub` to `WebSocket`
* `WebSocketStub` and `WebSocket` are supplied to the websocket-consuming functions, if the response specification describes such interactions
* for non-blocking, asynchronous streaming responses, any provided value is treated as a raw stream value; the value should be of type `sttp.capabilities.Streams.BinaryStream`, and if that's not the case, a `ClassCastException` might be thrown
* any of the above to custom types through mapped response specifications

## Example: returning JSON

For example, if you want to return a JSON response, simply use `.withResponse(String)` as below:

```scala mdoc:compile-only
val testingBackend = SyncBackendStub
  .whenRequestMatches(_ => true)
  .thenRespondAdjust(""" {"username": "john", "age": 65 } """)

def parseUserJson(a: Array[Byte]): User = ???

val response = basicRequest.get(uri"http://example.com")
  .response(asByteArrayAlways.map(parseUserJson))
  .send(testingBackend)
```

In the example above, the stub's rules specify that a response with a `String`-body should be returned for any request; the request, on the other hand, specifies that response body should be parsed from a byte array to a custom `User` type. These type don't match, so the `BackendStub` will in this case convert the body to the desired type.

## Example: returning a file

If you want to save the response to a file and have the response handler set up like this:

```scala mdoc:compile-only
val destination = new File("path/to/file.ext")
basicRequest.get(uri"http://example.com").response(asFile(destination))
```

With the stub created as follows:

```scala mdoc:compile-only
val fileResponseHandle = new File("path/to/file.ext")
SyncBackendStub
  .whenRequestMatches(_ => true)
  .thenRespondAdjust(fileResponseHandle)
```

the `File` set up in the stub will be returned as though it was the `File` set up as `destination` in the response handler above. This means that the file from `fileResponseHandle` is not written to `destination`.

If you actually want a file to be written you can set up the stub like this:

```scala mdoc:compile-only
import org.apache.commons.io.FileUtils
import cats.effect.*
import sttp.client4.impl.cats.implicits.*
import sttp.monad.MonadAsyncError

val sourceFile = new File("path/to/file.ext")
val destinationFile = new File("path/to/file.ext")
BackendStub(implicitly[MonadAsyncError[IO]])
  .whenRequestMatches(_ => true)
  .thenRespondF: _ =>
    FileUtils.copyFile(sourceFile, destinationFile)
    IO(ResponseStub.adjust(destinationFile, StatusCode.Ok))
```

## Responding with bodies as-is

Alternatively, response bodies can be provided as-is, without any adjustment attempts, regardless of the request's response description. To do that, use `.thenRespondExact` or `ResponseStub.exact`. If the desired response's type is not the same as the provided one, a `ClassCastException` will be thrown. For example:

```scala mdoc:compile-only
case class User(name: String)
val someUser: User = ???

// coming from a JSON integration
def asJson[T]: ResponseAs[Either[String, T]] = ???

val testingBackend = SyncBackendStub
  .whenRequestMatches(_ => true)
  .thenRespondExact(Right(someUser))

val response = basicRequest.get(uri"http://example.com")
  .response(asJson[User])
  .send(testingBackend)
```

## Delegating to another backend

It is also possible to create a stub backend which delegates calls to another (possibly "real") backend if none of the specified predicates match a request. This can be useful during development, to partially stub a yet incomplete API with which we integrate:

```scala mdoc:compile-only
val testingBackend =
  SyncBackendStub.withFallback(DefaultSyncBackend())
    .whenRequestMatches(_.uri.path.startsWith(List("a")))
    .thenRespondAdjust("I'm a STUB!")

val response1 = basicRequest.get(uri"http://api.internal/a").send(testingBackend)
// response1.body will be Right("I'm a STUB")

val response2 = basicRequest.post(uri"http://api.internal/b").send(testingBackend)
// response2 will be whatever a "real" network call to api.internal/b returns
```

## Testing streams

Streaming responses can be stubbed the same as ordinary values. The body of the response should contain the raw byte stream value, of type `sttp.capabilities.Streams.BinaryStream`.

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

val backend = WebSocketBackendStub.synchronous
val webSocketStub = WebSocketStub
  .initialReceive(
    List(WebSocketFrame.text("Hello from the server!"))
  )
  .thenRespondS(0):
    case (counter, tf: WebSocketFrame.Text) => (counter + 1, List(WebSocketFrame.text(s"echo: ${tf.payload}")))
    case (counter, _)                       => (counter, List.empty)

backend.whenAnyRequest.thenRespondAdjust(webSocketStub)
```

There is a possiblity to add error responses as well. If this is not enough, using a custom implementation of
the `WebSocket` trait is recommended.

## Verifying that a request was sent

Using `RecordingSttpBackend` it's possible to capture all interactions in which a backend has been involved.

The recording backend is a [backend wrapper](../backends/wrappers/custom.md), and it can wrap any backend, but it's most
useful when combined with the backend stub.

Example usage:

```scala mdoc:compile-only
import scala.util.Try

val testingBackend = RecordingBackend(
  SyncBackendStub
    .whenRequestMatches(_.uri.path.startsWith(List("a", "b")))
    .thenRespondAdjust("Hello there!")
)

val response1 = basicRequest.get(uri"http://example.org/a/b/c").send(testingBackend)
// response1.body will be Right("Hello there")

testingBackend.allInteractions: List[(GenericRequest[_, _], Try[Response[_]])]
// the list will contain one element and can be verified in a test
```
