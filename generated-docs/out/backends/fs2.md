# fs2 backend

The [fs2](https://github.com/functional-streams-for-scala/fs2) backends are **asynchronous**. They can be created for any type implementing the `cats.effect.Async` typeclass, such as `cats.effect.IO`. Sending a request is a non-blocking, lazily-evaluated operation and results in a wrapped response. There's a transitive dependency on `cats-effect`. 

## Using HttpClient

Creation of the backend can be done in two basic ways:

* by creating a `Resource`, which will instantiate the backend and close it after it has been used.
* by creating an effect, which describes how a backend is created, or instantiating the backend directly. In this case, you'll need to close the backend manually, as well as provide a `Dispatcher` instance

Firstly, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client4" %% "fs2" % "4.0.0-M19" // for cats-effect 3.x & fs2 3.x
// or 
"com.softwaremill.sttp.client4" %% "fs2ce2" % "4.0.0-M19" // for cats-effect 2.x & fs2 2.x
```

Obtain a cats-effect `Resource` which creates the backend, and closes the thread pool after the resource is no longer used:

```scala
import cats.effect.IO
import sttp.client4.httpclient.fs2.HttpClientFs2Backend

HttpClientFs2Backend.resource[IO]().use { backend => ??? }
```

or, by providing a custom `Dispatcher`:

```scala
import cats.effect.IO
import cats.effect.std.Dispatcher
import sttp.client4.httpclient.fs2.HttpClientFs2Backend

val dispatcher: Dispatcher[IO] = ???

HttpClientFs2Backend[IO](dispatcher).flatMap { backend => ??? }
```

or, if you'd like to instantiate the `HttpClient` yourself:

```scala
import cats.effect.IO
import cats.effect.std.Dispatcher
import java.net.http.HttpClient
import sttp.client4.httpclient.fs2.HttpClientFs2Backend

val httpClient: HttpClient = ???
val dispatcher: Dispatcher[IO] = ???

val backend = HttpClientFs2Backend.usingClient[IO](httpClient, dispatcher)
```

or, obtain a cats-effect `Resource` with a custom instance of the `HttpClient`:

```scala
import cats.effect.IO
import java.net.http.HttpClient
import sttp.client4.httpclient.fs2.HttpClientFs2Backend

val httpClient: HttpClient = ???

HttpClientFs2Backend.resourceUsingClient[IO](httpClient).use { backend => ??? }
```

This backend is based on the built-in `java.net.http.HttpClient` available from Java 11 onwards.

Host header override is supported in environments running Java 12 onwards, but it has to be enabled by system property:

```
-Djdk.httpclient.allowRestrictedHeaders=host
```


## Using Armeria

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client4" %% "armeria-backend-fs2" % "4.0.0-M19" // for cats-effect 3.x & fs2 3.x
// or
"com.softwaremill.sttp.client4" %% "armeria-backend-fs2" % "4.0.0-M19" // for cats-effect 2.x & fs2 2.x
```

create client:

```scala
import cats.effect.IO
import cats.effect.std.Dispatcher
import sttp.client4.armeria.fs2.ArmeriaFs2Backend

ArmeriaFs2Backend.resource[IO]().use { backend => ??? }

val dispatcher: Dispatcher[IO] = ???
// You can use the default client which reuses the connection pool of ClientFactory.ofDefault()
ArmeriaFs2Backend.usingDefaultClient[IO](dispatcher)
```

or, if you'd like to instantiate the [WebClient](https://armeria.dev/docs/client-http) yourself:

```scala
import cats.effect.IO
import cats.effect.std.Dispatcher
import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.client.circuitbreaker._
import sttp.client4.armeria.fs2.ArmeriaFs2Backend

val dispatcher: Dispatcher[IO] = ???

// Fluently build Armeria WebClient with built-in decorators
val client = WebClient.builder("https://my-service.com")
             // Open circuit on 5xx server error status
             .decorator(CircuitBreakerClient.newDecorator(CircuitBreaker.ofDefaultName(),
               CircuitBreakerRule.onServerErrorStatus()))
             .build()
             
val backend = ArmeriaFs2Backend.usingClient[IO](client, dispatcher)
```

```eval_rst
.. note:: A WebClient could fail to follow redirects if the WebClient is created with a base URI and a redirect location is a different URI.
```

This backend is built on top of [Armeria](https://armeria.dev/docs/client-http).
Armeria's [ClientFactory](https://armeria.dev/docs/client-factory) manages connections and protocol-specific properties.
Please visit [the official documentation](https://armeria.dev/docs/client-factory) to learn how to configure it.

## Streaming

The fs2 backends support streaming for any instance of the `cats.effect.Effect` typeclass, such as `cats.effect.IO`. If `IO` is used then the type of supported streams is `fs2.Stream[IO, Byte]`. The streams capability is represented as `sttp.client4.fs2.Fs2Streams`.

Requests can be sent with a streaming body like this:

```scala
import cats.effect.IO
import fs2.Stream
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4._
import sttp.client4.httpclient.fs2.HttpClientFs2Backend

val effect = HttpClientFs2Backend.resource[IO]().use { backend =>
  val stream: Stream[IO, Byte] = ???

  basicRequest
    .post(uri"...")
    .streamBody(Fs2Streams[IO])(stream)
    .send(backend)
}
// run the effect
```

Responses can also be streamed:

```scala
import cats.effect.IO
import fs2.Stream
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4._
import sttp.client4.httpclient.fs2.HttpClientFs2Backend
import scala.concurrent.duration.Duration

val effect = HttpClientFs2Backend.resource[IO]().use { backend =>
  val response: IO[Response[Either[String, Stream[IO, Byte]]]] =
    basicRequest
      .post(uri"...")
      .response(asStreamUnsafe(Fs2Streams[IO]))
      .readTimeout(Duration.Inf)
      .send(backend)

  response
}
// run the effect
```

## Websockets

The fs2 backends support both regular and streaming [websockets](../websockets.md).

## Server-sent events

Received data streams can be parsed to a stream of server-sent events (SSE):

```scala
import cats.effect._
import fs2.Stream
import sttp.client4._
import sttp.capabilities.fs2.Fs2Streams
import sttp.client4.impl.fs2.Fs2ServerSentEvents
import sttp.model.sse.ServerSentEvent

def processEvents(source: Stream[IO, ServerSentEvent]): IO[Unit] = ???

basicRequest
  .get(uri"")
  .response(asStream(Fs2Streams[IO])(stream =>
    processEvents(stream.through(Fs2ServerSentEvents.parse))))
```
