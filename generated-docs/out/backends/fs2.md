# fs2 backend

The [fs2](https://github.com/functional-streams-for-scala/fs2) backend is **asynchronous**. It can be created for any type implementing the `cats.effect.Async` typeclass, such as `cats.effect.IO`. Sending a request is a non-blocking, lazily-evaluated operation and results in a wrapped response. There's a transitive dependency on `cats-effect`. 

## Using async-http-client

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client3" %% "async-http-client-backend-fs2" % "3.3.10" // for cats-effect 3.x & fs2 3.x
// or
"com.softwaremill.sttp.client3" %% "async-http-client-backend-fs2-ce2" % "3.3.10" // for cats-effect 2.x & fs2 2.x
```
 
This backend depends on [async-http-client](https://github.com/AsyncHttpClient/async-http-client) and uses [Netty](http://netty.io) behind the scenes.

Next you'll need to define a backend instance as an implicit value. This can be done in two basic ways:

* by creating a `Resource`, which will instantiate the backend (along with a `Dispatcher`) and close it after it has been used
* by creating an effect, which describes how a backend is created, or instantiating the backend directly. In this case, you'll need to close the backend manually, as well as provide a `Dispatcher` instance

Below you can find a non-comprehensive summary of how the backend can be created. The easiest form is to use a cats-effect `Resource`:

```scala
import cats.effect.IO
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend

AsyncHttpClientFs2Backend.resource[IO]().use { backend => ??? }
```

or, by providing a custom dispatcher:

```scala
import cats.effect.IO
import cats.effect.std.Dispatcher
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend

val dispatcher: Dispatcher[IO] = ???

AsyncHttpClientFs2Backend[IO](dispatcher).flatMap { backend => ??? }
```

or, if you'd like to use a custom configuration:

```scala
import cats.effect.IO
import cats.effect.std.Dispatcher
import org.asynchttpclient.AsyncHttpClientConfig
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend

val dispatcher: Dispatcher[IO] = ???

val config: AsyncHttpClientConfig = ???
AsyncHttpClientFs2Backend.usingConfig[IO](config, dispatcher).flatMap { backend => ??? }
```

or, if you'd like to use adjust the configuration sttp creates:

```scala
import cats.effect.IO
import cats.effect.std.Dispatcher
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import sttp.client3.SttpBackendOptions
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend

val sttpOptions: SttpBackendOptions = SttpBackendOptions.Default  
val adjustFunction: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder = ???
val dispatcher: Dispatcher[IO] = ???

AsyncHttpClientFs2Backend.usingConfigBuilder[IO](dispatcher, adjustFunction, sttpOptions).flatMap { backend => ??? }
```

or, if you'd like to instantiate the AsyncHttpClient yourself:

```scala
import cats.effect.IO
import cats.effect.std.Dispatcher
import org.asynchttpclient.AsyncHttpClient
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend

val dispatcher: Dispatcher[IO] = ???
val asyncHttpClient: AsyncHttpClient = ???  
val backend = AsyncHttpClientFs2Backend.usingClient[IO](asyncHttpClient, dispatcher)
```

## Using HttpClient (Java 11+)

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client3" %% "httpclient-backend-fs2" % "3.3.10" // for cats-effect 3.x & fs2 3.x
// or 
"com.softwaremill.sttp.client3" %% "httpclient-backend-fs2-ce2" % "3.3.10" // for cats-effect 2.x & fs2 2.x
```

Create the backend using a cats-effect `Resource`:

```scala
import cats.effect.IO
import sttp.client3.httpclient.fs2.HttpClientFs2Backend

HttpClientFs2Backend.resource[IO]().use { backend => ??? }
```

or, if by providing a custom `Dispatcher`:

```scala
import cats.effect.IO
import cats.effect.std.Dispatcher
import sttp.client3.httpclient.fs2.HttpClientFs2Backend

val dispatcher: Dispatcher[IO] = ???

HttpClientFs2Backend[IO](dispatcher).flatMap { backend => ??? }
```

or, if you'd like to instantiate the `HttpClient` yourself:

```scala
import cats.effect.IO
import cats.effect.std.Dispatcher
import java.net.http.HttpClient
import sttp.client3.httpclient.fs2.HttpClientFs2Backend

val httpClient: HttpClient = ???
val dispatcher: Dispatcher[IO] = ???

val backend = HttpClientFs2Backend.usingClient[IO](httpClient, dispatcher)
```

This backend is based on the built-in `java.net.http.HttpClient` available from Java 11 onwards.

Host header override is supported in environments running Java 12 onwards, but it has to be enabled by system property:
```
jdk.httpclient.allowRestrictedHeaders=host
```

## Using Armeria

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client3" %% "armeria-backend-fs2" % "3.3.10" // for cats-effect 3.x & fs2 3.x
// or
"com.softwaremill.sttp.client3" %% "armeria-backend-fs2" % "3.3.10" // for cats-effect 2.x & fs2 2.x
```

create client:

```scala
import cats.effect.IO
import cats.effect.std.Dispatcher
import sttp.client3.armeria.fs2.ArmeriaFs2Backend

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
import sttp.client3.armeria.fs2.ArmeriaFs2Backend

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

The fs2 backend supports streaming for any instance of the `cats.effect.Effect` typeclass, such as `cats.effect.IO`. If `IO` is used then the type of supported streams is `fs2.Stream[IO, Byte]`. The streams capability is represented as `sttp.client3.fs2.Fs2Streams`.

Requests can be sent with a streaming body like this:

```scala
import cats.effect.IO
import fs2.Stream
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.client3.armeria.fs2.ArmeriaFs2Backend
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend

val effect = AsyncHttpClientFs2Backend.resource[IO]().use { backend =>
  val stream: Stream[IO, Byte] = ???

  basicRequest
    .streamBody(Fs2Streams[IO])(stream)
    .post(uri"...")
    .send(backend)
}
```

Responses can also be streamed:

```scala
import cats.effect.IO
import fs2.Stream
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3._
import sttp.client3.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import scala.concurrent.duration.Duration

val effect = AsyncHttpClientFs2Backend.resource[IO]().use { backend =>
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
import sttp.client3._
import sttp.capabilities.fs2.Fs2Streams
import sttp.client3.impl.fs2.Fs2ServerSentEvents
import sttp.model.sse.ServerSentEvent

def processEvents(source: Stream[IO, ServerSentEvent]): IO[Unit] = ???

basicRequest.response(asStream(Fs2Streams[IO])(stream => 
  processEvents(stream.through(Fs2ServerSentEvents.parse))))
```
