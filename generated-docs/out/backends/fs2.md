# fs2 backend

The [fs2](https://github.com/functional-streams-for-scala/fs2) backend is **asynchronous**. It can be created for any type implementing the `cats.effect.Async` typeclass, such as `cats.effect.IO`. Sending a request is a non-blocking, lazily-evaluated operation and results in a wrapped response. There's a transitive dependency on `cats-effect`. 

## Using async-http-client

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client" %% "async-http-client-backend-fs2" % "2.2.6"
```
And some imports:
```scala
import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import cats.effect._
import sttp.client._

// an implicit `cats.effect.ContextShift` in required to create an instance of `cats.effect.Concurrent`
// for `cats.effect.IO`:
implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
```
           
This backend depends on [async-http-client](https://github.com/AsyncHttpClient/async-http-client) and uses [Netty](http://netty.io) behind the scenes.

Next you'll need to define a backend instance as an implicit value. This can be done in two basic ways:

* by creating an effect, which describes how a backend is created, or instantiating the backend directly. In this case, you'll need to close the backend manually
* by creating a `Resource`, which will instantiate the backend and close it after it has been used

A non-comprehensive summary of how the backend can be created is as follows:

```scala
AsyncHttpClientFs2Backend[IO]().flatMap { implicit backend => ??? }
```
or, if you'd like to use a custom configuration:
```scala
import org.asynchttpclient.AsyncHttpClientConfig

val config: AsyncHttpClientConfig = ???
AsyncHttpClientFs2Backend.usingConfig[IO](config).flatMap { implicit backend => ??? }
```
or, if you'd like to use adjust the configuration sttp creates:
```scala
import org.asynchttpclient.DefaultAsyncHttpClientConfig

val sttpOptions: SttpBackendOptions = SttpBackendOptions.Default  
val adjustFunction: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder = ???
AsyncHttpClientFs2Backend.usingConfigBuilder[IO](adjustFunction, sttpOptions).flatMap { implicit backend => ??? }
```
or, if you'd like the backend to be wrapped in cats-effect Resource:
```scala
AsyncHttpClientFs2Backend.resource[IO]().use { implicit backend => ??? }
```
or, if you'd like to instantiate the AsyncHttpClient yourself:
```scala
import org.asynchttpclient.AsyncHttpClient

val asyncHttpClient: AsyncHttpClient = ???  
implicit val sttpBackend = AsyncHttpClientFs2Backend.usingClient[IO](asyncHttpClient)
```

## Using HttpClient (Java 11+)

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client" %% "httpclient-backend-fs2" % "2.2.6"
```
And some imports:
```scala
import sttp.client.httpclient.fs2.HttpClientFs2Backend
import cats.effect._
import sttp.client._

// an implicit `cats.effect.ContextShift` is required to create a concurrent instance for `cats.effect.IO`,
// as well as a `cats.effect.Blocker` instance. Note that you'll probably want to use a different thread
// pool for blocking.
implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
val blocker = Blocker.liftExecutionContext(scala.concurrent.ExecutionContext.global)
```

Create the backend using:

```scala
import sttp.client.httpclient.fs2.HttpClientFs2Backend
HttpClientFs2Backend[IO](blocker).flatMap { implicit backend => ??? }
```
or, if you'd like the backend to be wrapped in cats-effect Resource:
```scala
HttpClientFs2Backend.resource[IO](blocker).use { implicit backend => ??? }
```
or, if you'd like to instantiate the HttpClient yourself:
```scala
import java.net.http.HttpClient
val httpClient: HttpClient = ???
implicit val sttpBackend = HttpClientFs2Backend.usingClient[IO](httpClient, blocker)
```

This backend is based on the built-in `java.net.http.HttpClient` available from Java 11 onwards.

## Streaming

The fs2 backend supports streaming for any instance of the `cats.effect.Effect` typeclass, such as `cats.effect.IO`. If `IO` is used then the type of supported streams is `fs2.Stream[IO, Byte]`.

Requests can be sent with a streaming body like this:

```scala
import sttp.client._
import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import fs2.Stream

val effect = AsyncHttpClientFs2Backend[IO]().flatMap { implicit backend =>
  val stream: Stream[IO, Byte] = ???

  basicRequest
    .streamBody(stream)
    .post(uri"...")
    .send()
}
```

Responses can also be streamed:

```scala
import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import fs2.Stream
import scala.concurrent.duration.Duration

val effect = AsyncHttpClientFs2Backend[IO]().flatMap { implicit backend =>
  val response: IO[Response[Either[String, Stream[IO, Byte]]]] =
    basicRequest
      .post(uri"...")
      .response(asStream[Stream[IO, Byte]])
      .readTimeout(Duration.Inf)
      .send()

  response
}
```

## Websockets

The fs2 backend supports:

* high-level, "functional" websocket interface, through the `sttp.client.asynchttpclient.fs2.Fs2WebSocketHandler` or `sttp.client.httpclient.fs2.Fs2WebSocketHandler`
* low-level interface by wrapping a low-level Java interface, `sttp.client.asynchttpclient.WebSocketHandler` or `sttp.client.httpclient.WebSocketHandler`
* streaming - see below

See [websockets](../websockets.md) for details on how to use the high-level and low-level interfaces.

## Streaming websockets 

There are additionally high-level helpers collected in `sttp.client.asynchttpclient.fs2.Fs2Websockets` which provide means to run the whole websocket communication through an `fs2.Pipe`. Example for a simple echo client:

```scala
import sttp.client.ws._
import sttp.model.ws.WebSocketFrame
import sttp.client.asynchttpclient.fs2._
import sttp.client.impl.fs2._
import sttp.client.asynchttpclient.WebSocketHandler
import cats.implicits._

implicit val backend: SttpBackend[IO, fs2.Stream[IO, Byte], WebSocketHandler] = ???
basicRequest
  .get(uri"wss://echo.websocket.org")
  .openWebsocketF(Fs2WebSocketHandler[IO]())
  .flatMap { response =>
    Fs2WebSockets.handleSocketThroughTextPipe(response.result) { in =>
      val receive = in.evalMap(m => IO(println("Received")))
      val send = fs2.Stream("Message 1".asRight, "Message 2".asRight, WebSocketFrame.close.asLeft)
      send merge receive.drain
    }
  }
```
