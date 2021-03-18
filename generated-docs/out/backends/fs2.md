# fs2 backend

The [fs2](https://github.com/functional-streams-for-scala/fs2) backend is **asynchronous**. It can be created for any type implementing the `cats.effect.Async` typeclass, such as `cats.effect.IO`. Sending a request is a non-blocking, lazily-evaluated operation and results in a wrapped response. There's a transitive dependency on `cats-effect`. 

## Using async-http-client

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client3" %% "async-http-client-backend-fs2" % "3.1.9"
```

And some imports:

```scala
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import cats.effect._
import sttp.client3._

// an implicit `cats.effect.ContextShift` is required to create a concurrent instance for `cats.effect.IO`,
// as well as a `cats.effect.Blocker` instance. Note that you'll probably want to use a different thread
// pool for blocking.
implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
val blocker: Blocker = Blocker.liftExecutionContext(scala.concurrent.ExecutionContext.global)
```
           
This backend depends on [async-http-client](https://github.com/AsyncHttpClient/async-http-client) and uses [Netty](http://netty.io) behind the scenes.

Next you'll need to define a backend instance as an implicit value. This can be done in two basic ways:

* by creating an effect, which describes how a backend is created, or instantiating the backend directly. In this case, you'll need to close the backend manually
* by creating a `Resource`, which will instantiate the backend and close it after it has been used

A non-comprehensive summary of how the backend can be created is as follows:

```scala
AsyncHttpClientFs2Backend[IO](blocker).flatMap { backend => ??? }
```

or, if you'd like to use a custom configuration:

```scala
import org.asynchttpclient.AsyncHttpClientConfig

val config: AsyncHttpClientConfig = ???
AsyncHttpClientFs2Backend.usingConfig[IO](blocker, config).flatMap { backend => ??? }
```

or, if you'd like to use adjust the configuration sttp creates:

```scala
import org.asynchttpclient.DefaultAsyncHttpClientConfig

val sttpOptions: SttpBackendOptions = SttpBackendOptions.Default  
val adjustFunction: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder = ???
AsyncHttpClientFs2Backend.usingConfigBuilder[IO](blocker, adjustFunction, sttpOptions).flatMap { backend => ??? }
```

or, if you'd like the backend to be wrapped in cats-effect Resource:

```scala
AsyncHttpClientFs2Backend.resource[IO](blocker).use { backend => ??? }
```

or, if you'd like to instantiate the AsyncHttpClient yourself:

```scala
import org.asynchttpclient.AsyncHttpClient

val asyncHttpClient: AsyncHttpClient = ???  
val backend = AsyncHttpClientFs2Backend.usingClient[IO](asyncHttpClient, blocker)
```

## Using HttpClient (Java 11+)

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "httpclient-backend-fs2" % "3.1.9"
```

And some imports:

```scala
import sttp.client3.httpclient.fs2.HttpClientFs2Backend
import cats.effect._
import sttp.client3._

// an implicit `cats.effect.ContextShift` is required to create a concurrent instance for `cats.effect.IO`,
// as well as a `cats.effect.Blocker` instance. Note that you'll probably want to use a different thread
// pool for blocking.
implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
val blocker = Blocker.liftExecutionContext(scala.concurrent.ExecutionContext.global)
```

Create the backend using:

```scala
import sttp.client3.httpclient.fs2.HttpClientFs2Backend
HttpClientFs2Backend[IO](blocker).flatMap { backend => ??? }
```

or, if you'd like the backend to be wrapped in cats-effect Resource:

```scala
HttpClientFs2Backend.resource[IO](blocker).use { backend => ??? }
```

or, if you'd like to instantiate the HttpClient yourself:

```scala
import java.net.http.HttpClient
val httpClient: HttpClient = ???
val backend = HttpClientFs2Backend.usingClient[IO](httpClient, blocker)
```

This backend is based on the built-in `java.net.http.HttpClient` available from Java 11 onwards.

Host header override is supported in environments running Java 12 onwards, but it has to be enabled by system property:
```
jdk.httpclient.allowRestrictedHeaders=host
```

## Using Armeria

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "armeria-backend-fs2" % "3.1.9"
```

add imports:

```scala
import sttp.client3.armeria.fs2.ArmeriaFs2Backend
import cats.effect.{ContextShift, IO}
```

create client:

```scala
implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
val backend = ArmeriaFs2Backend[IO]()
```

or, if you'd like to instantiate the [WebClient](https://armeria.dev/docs/client-http) yourself:

```scala
import com.linecorp.armeria.client.circuitbreaker._
import com.linecorp.armeria.client.WebClient

// Fluently build Armeria WebClient with built-in decorators
val client = WebClient.builder("https://my-service.com")
             // Open circuit on 5xx server error status
             .decorator(CircuitBreakerClient.newDecorator(CircuitBreaker.ofDefaultName(),
               CircuitBreakerRule.onServerErrorStatus()))
             .build()
             
val backend = ArmeriaFs2Backend.usingClient[IO](client)
```

```eval_rst
.. note:: A WebClient could fail to follow redirects if the WebClient is created with a base URI and a redirect location is a different URI.
```

This backend is build on top of [Armeria](https://armeria.dev/docs/client-http).

## Streaming

The fs2 backend supports streaming for any instance of the `cats.effect.Effect` typeclass, such as `cats.effect.IO`. If `IO` is used then the type of supported streams is `fs2.Stream[IO, Byte]`. The streams capability is represented as `sttp.client3.fs2.Fs2Streams`.

Requests can be sent with a streaming body like this:

```scala
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import fs2.Stream

val effect = AsyncHttpClientFs2Backend[IO](blocker).flatMap { backend =>
  val stream: Stream[IO, Byte] = ???

  basicRequest
    .streamBody(Fs2Streams[IO])(stream)
    .post(uri"...")
    .send(backend)
}
```

Responses can also be streamed:

```scala
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import fs2.Stream
import scala.concurrent.duration.Duration

val effect = AsyncHttpClientFs2Backend[IO](blocker).flatMap { backend =>
  val response: IO[Response[Either[String, Stream[IO, Byte]]]] =
    basicRequest
      .post(uri"...")
      .response(asStreamUnsafe(Fs2Streams[IO]))
      .readTimeout(Duration.Inf)
      .send(backend)

  response
}
```

## Websockets

The fs2 backend supports both regular and streaming [websockets](../websockets.md).

## Server-sent events

Received data streams can be parsed to a stream of server-sent events (SSE):

```scala
import cats.effect._
import fs2.Stream

import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.impl.fs2.Fs2ServerSentEvents
import sttp.model.sse.ServerSentEvent
import sttp.client3._

def processEvents(source: Stream[IO, ServerSentEvent]): IO[Unit] = ???

basicRequest.response(asStream(Fs2Streams[IO])(stream => 
  processEvents(stream.through(Fs2ServerSentEvents.parse))))
```
