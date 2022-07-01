
# ZIO backends

The [ZIO](https://github.com/zio/zio) backends are **asynchronous**. Sending a request is a non-blocking, lazily-evaluated operation and results in a response wrapped in a `zio.Task`. There's a transitive dependency on the `zio` or `zio1` modules.

The `*-zio` modules depend on ZIO 2.x. For ZIO 1.x support, use modules with the `*-zio1` suffix.

## Using HttpClient

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "zio" % "@VERSION@"  // for ZIO 2.x
"com.softwaremill.sttp.client3" %% "zio1" % "@VERSION@" // for ZIO 1.x
```

Create the backend using:

```scala mdoc:compile-only
import sttp.client3.httpclient.zio.HttpClientZioBackend

HttpClientZioBackend().flatMap { backend => ??? }

// or, if you'd like the backend to be created in a Scope:
HttpClientZioBackend.scoped().flatMap { backend => ??? }

// or, if you'd like to instantiate the HttpClient yourself:
import java.net.http.HttpClient
val httpClient: HttpClient = ???
val backend = HttpClientZioBackend.usingClient(httpClient)
```

This backend is based on the built-in `java.net.http.HttpClient` available from Java 11 onwards. The backend is fully non-blocking, with back-pressured websockets.

Host header override is supported in environments running Java 12 onwards, but it has to be enabled by system property:

```
-Djdk.httpclient.allowRestrictedHeaders=host
```

## Using async-http-client

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % "@VERSION@"  // for ZIO 2.x
"com.softwaremill.sttp.client3" %% "async-http-client-backend-zio1" % "@VERSION@" // for ZIO 1.x
```
           
This backend depends on [async-http-client](https://github.com/AsyncHttpClient/async-http-client), uses [Netty](http://netty.io) behind the scenes. This backend works with all Scala versions. A Scala 3 build is available as well.

Next you'll need to define a backend instance.  A non-comprehensive summary of how this can be done is as follows:

```scala mdoc:compile-only
import sttp.client3._
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend

AsyncHttpClientZioBackend().flatMap { backend => ??? }

// or, if you'd like the backend to be wrapped in a Scope:
AsyncHttpClientZioBackend.scoped().flatMap { backend => ??? }

// or, if you'd like to use custom configuration:
import org.asynchttpclient.AsyncHttpClientConfig
val config: AsyncHttpClientConfig = ???
AsyncHttpClientZioBackend.usingConfig(config).flatMap { backend => ??? }

// or, if you'd like to use adjust the configuration sttp creates:
import org.asynchttpclient.DefaultAsyncHttpClientConfig
val sttpOptions: SttpBackendOptions = SttpBackendOptions.Default 
val adjustFunction: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder = ???

AsyncHttpClientZioBackend.usingConfigBuilder(adjustFunction, sttpOptions).flatMap { backend => ??? }

// or, if you'd like to instantiate the AsyncHttpClient yourself:
import org.asynchttpclient.AsyncHttpClient
import zio.Runtime
val asyncHttpClient: AsyncHttpClient = ???
val runtime: Runtime[Any] = ???
val backend = AsyncHttpClientZioBackend.usingClient(runtime, asyncHttpClient)
```

## Using Armeria

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "armeria-backend-zio" % "@VERSION@"  // for ZIO 2.x
"com.softwaremill.sttp.client3" %% "armeria-backend-zio1" % "@VERSION@" // for ZIO 1.x
```

add imports:

```scala mdoc:silent
import sttp.client3.armeria.zio.ArmeriaZioBackend
```

create client:

```scala mdoc:compile-only
ArmeriaZioBackend().flatMap { backend => ??? }

// or, if you'd like the backend to be wrapped in a Scope:
ArmeriaZioBackend.scoped().flatMap { backend => ??? }

// You can use the default client which reuses the connection pool of ClientFactory.ofDefault()
ArmeriaZioBackend.usingDefaultClient().flatMap { backend => ??? }
```

```eval_rst
.. note:: The default client factory is reused to create `ArmeriaZioBackend` if a `SttpBackendOptions` is unspecified. So you only need to manage a resource when `SttpBackendOptions` is used.
```

or, if you'd like to instantiate the [WebClient](https://armeria.dev/docs/client-http) yourself:

```scala mdoc:compile-only
import com.linecorp.armeria.client.circuitbreaker._
import com.linecorp.armeria.client.WebClient

// Fluently build Armeria WebClient with built-in decorators
val client = WebClient.builder("https://my-service.com")
             // Open circuit on 5xx server error status
             .decorator(CircuitBreakerClient.newDecorator(CircuitBreaker.ofDefaultName(),
               CircuitBreakerRule.onServerErrorStatus()))
             .build()

ArmeriaZioBackend.usingClient(client).flatMap { backend => ??? }
```

```eval_rst
.. note:: A WebClient could fail to follow redirects if the WebClient is created with a base URI and a redirect location is a different URI.
```

This backend is build on top of [Armeria](https://armeria.dev/docs/client-http).
Armeria's [ClientFactory](https://armeria.dev/docs/client-factory) manages connections and protocol-specific properties.
Please visit [the official documentation](https://armeria.dev/docs/client-factory) to learn how to configure it.

## ZIO environment

As an alternative to effectfully or resourcefully creating backend instances, ZIO environment can be used. In this case, a type aliases are provided for the service definition:

```scala
type SttpClient = SttpBackend[Task, ZioStreams]  // as base definition 

// or, for backends that does supports WebSockets
type SttpClientWebSockets = SttpBackend[Task, ZioStreams with WebSockets]

```

`SttpClientWebSockets` is just an extension of `SttpClient` which adds WebSockets capabilities to underlying backend

The lifecycle of the `SttpClient` service is described by `ZLayer`s, which can be created using the `.layer`/`.layerUsingConfig`/... methods on `AsyncHttpClientZioBackend` / `HttpClientZioBackend` / `ArmeriaZioBackend`.

The `SttpClient` companion object contains effect descriptions which use the `SttpClient` service from the environment to send requests or open websockets. This is different from sttp usage with other effect libraries (which use an implicit backend when `.send(backend)` is invoked on the request), but is more in line with how other ZIO services work. For example:

```scala mdoc:compile-only
import sttp.client3._
import sttp.client3.impl.zio.{SttpClientWebSockets, sendWebSockets}
import zio._
val request = basicRequest.get(uri"https://httpbin.org/get")

val sent: ZIO[SttpClientWebSockets, Throwable, Response[Either[String, String]]] = 
  sendWebSockets(request)
```

## Streaming

The ZIO based backends support streaming using zio-streams. The following example is using the `AsyncHttpClientZioBackend` backend, but works similarly with `HttpClientZioBackend`.

The type of supported streams is `Stream[Throwable, Byte]`. The streams capability is represented as `sttp.client3.impl.zio.ZioStreams`. To leverage ZIO environment, use the `SttpClient` object to create request send effects.

Requests can be sent with a streaming body:

```scala mdoc:compile-only
import sttp.capabilities.zio.ZioStreams
import sttp.client3._
import sttp.client3.impl.zio.sendWebSockets
import zio.stream._

val s: Stream[Throwable, Byte] =  ???

val request = basicRequest
  .streamBody(ZioStreams)(s)
  .post(uri"...")

sendWebSockets(request)
```

And receive response bodies as a stream:

```scala mdoc:compile-only
import sttp.capabilities.zio.ZioStreams
import sttp.client3._
import sttp.client3.impl.zio.{SttpClientWebSockets, sendWebSockets}

import zio._
import zio.stream._

import scala.concurrent.duration.Duration

val request =
  basicRequest
    .post(uri"...")
    .response(asStreamUnsafe(ZioStreams))
    .readTimeout(Duration.Inf)

val response: ZIO[SttpClientWebSockets, Throwable, Response[Either[String, Stream[Throwable, Byte]]]] = sendWebSockets(request)
```

## Websockets

The ZIO backend supports both regular and streaming [websockets](../websockets.md).

## Testing

The ZIO backends also support a ZIO-familiar way of configuring [stubs](../testing.md) as well. In addition to the
usual way of creating a stand-alone stub, you can also define your stubs as effects instead:

```scala mdoc:compile-only
import sttp.client3._
import sttp.model._
import sttp.client3.httpclient._
import sttp.client3.httpclient.zio._
import sttp.client3.impl.zio.sendWebSockets
import sttp.client3.impl.zio.stubbingWebSockets._

val stubEffect = for {
  _ <- whenRequestMatches(_.uri.toString.endsWith("c")).thenRespond("c")
  _ <- whenRequestMatchesPartial { case r if r.method == Method.POST => Response.ok("b") }
  _ <- whenAnyRequest.thenRespond("a")
} yield ()

val responseEffect = stubEffect *> sendWebSockets(basicRequest.get(uri"http://example.org/a")).map(_.body)

responseEffect.provideLayer(HttpClientZioBackend.stubLayer) // Task[Either[String, String]]
```

The `whenRequestMatches`, `whenRequestMatchesPartial`, `whenAnyRequest` are effects which require the `SttpClientStubbingWebSockets`
dependency. They enrich the stub with the given behavior.

Then, the `stubLayer` provides both an implementation of the `SttpClientStubbingWebSockets` dependency, as well as a `SttpClientWebSockets`
which is backed by the stub.

## Server-sent events

Received data streams can be parsed to a stream of server-sent events (SSE):

```scala mdoc:compile-only
import zio._
import zio.stream._

import sttp.capabilities.zio.ZioStreams
import sttp.client3.impl.zio.ZioServerSentEvents
import sttp.model.sse.ServerSentEvent
import sttp.client3._

def processEvents(source: Stream[Throwable, ServerSentEvent]): Task[Unit] = ???

basicRequest.response(asStream(ZioStreams)(stream => 
  processEvents(stream.viaFunction(ZioServerSentEvents.parse))))
```
