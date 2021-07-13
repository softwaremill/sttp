# ZIO backends

The [ZIO](https://github.com/zio/zio) backends are **asynchronous**. Sending a request is a non-blocking, lazily-evaluated operation and results in a response wrapped in a `zio.Task`. There's a transitive dependency on `zio`.

## Using HttpClient (Java 11+)

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "httpclient-backend-zio" % "3.3.10"
```

Create the backend using:

```scala
import sttp.client3.httpclient.zio.HttpClientZioBackend

HttpClientZioBackend().flatMap { backend => ??? }

// or, if you'd like the backend to be wrapped in a Managed:
HttpClientZioBackend.managed().use { backend => ??? }

// or, if you'd like to instantiate the HttpClient yourself:
import java.net.http.HttpClient
val httpClient: HttpClient = ???
val backend = HttpClientZioBackend.usingClient(httpClient)
```

This backend is based on the built-in `java.net.http.HttpClient` available from Java 11 onwards. The backend is fully non-blocking, with back-pressured websockets.

Host header override is supported in environments running Java 12 onwards, but it has to be enabled by system property:
```
jdk.httpclient.allowRestrictedHeaders=host
```

## Using async-http-client

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % "3.3.10"
```
           
This backend depends on [async-http-client](https://github.com/AsyncHttpClient/async-http-client), uses [Netty](http://netty.io) behind the scenes. This backend works with all Scala versions. A Scala 3 build is available as well.

Next you'll need to define a backend instance as an implicit value. This can be done in two basic ways:

* by creating a `Task` which describes how the backend is created, or instantiating the backend directly. In this case, you'll need to close the backend manually
* by creating a `TaskManaged`, which will instantiate the backend and close it after it has been used

A non-comprehensive summary of how the backend can be created is as follows:

```scala
import sttp.client3._
import sttp.client3.asynchttpclient.zio.AsyncHttpClientZioBackend

AsyncHttpClientZioBackend().flatMap { backend => ??? }

// or, if you'd like the backend to be wrapped in a Managed:
AsyncHttpClientZioBackend.managed().use { backend => ??? }

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
"com.softwaremill.sttp.client3" %% "armeria-backend-zio" % "3.3.10"
```

add imports:

```scala
import sttp.client3.armeria.zio.ArmeriaZioBackend
```

create client:

```scala
ArmeriaZioBackend().flatMap { backend => ??? }

// or, if you'd like the backend to be wrapped in a Managed:
ArmeriaZioBackend.managed().use { backend => ??? }

// You can use the default client which reuses the connection pool of ClientFactory.ofDefault()
ArmeriaZioBackend.usingDefaultClient().flatMap { backend => ??? }
```

```eval_rst
.. note:: The default client factory is reused to create `ArmeriaZioBackend` if a `SttpBackendOptions` is unspecified. So you only need to manage a resource when `SttpBackendOptions` is used.
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

ArmeriaZioBackend.usingClient(client).flatMap { backend => ??? }
```

```eval_rst
.. note:: A WebClient could fail to follow redirects if the WebClient is created with a base URI and a redirect location is a different URI.
```

This backend is build on top of [Armeria](https://armeria.dev/docs/client-http).
Armeria's [ClientFactory](https://armeria.dev/docs/client-factory) manages connections and protocol-specific properties.
Please visit [the official documentation](https://armeria.dev/docs/client-factory) to learn how to configure it.

## ZIO environment

As an alternative to effectfully or resourcefully creating backend instances, ZIO environment can be used. In this case, a type alias is provided for the service definition:

```scala
package sttp.client3.httpclient.zio
type SttpClient = Has[SttpBackend[Task, ZioStreams with WebSockets]]

// or, when using async-http-client

package sttp.client3.asynchttpclient.zio
type SttpClient = Has[SttpBackend[Task, ZioStreams with WebSockets]]

// or, when using Armeria

package sttp.client3.armeria.zio
type SttpClient = Has[SttpBackend[Task, ZioStreams]]
```

The lifecycle of the `SttpClient` service is described by `ZLayer`s, which can be created using the `.layer`/`.layerUsingConfig`/... methods on `AsyncHttpClientZioBackend` / `HttpClientZioBackend` / `ArmeriaZioBackend`.

The `SttpClient` companion object contains effect descriptions which use the `SttpClient` service from the environment to send requests or open websockets. This is different from sttp usage with other effect libraries (which use an implicit backend when `.send(backend)` is invoked on the request), but is more in line with how other ZIO services work. For example:

```scala
import sttp.client3._
import sttp.client3.httpclient.zio._
import zio._
val request = basicRequest.get(uri"https://httpbin.org/get")

val sent: ZIO[SttpClient, Throwable, Response[Either[String, String]]] = 
  send(request)
```

## Streaming

The ZIO based backends support streaming using zio-streams. The following example is using the `AsyncHttpClientZioBackend` backend, but works similarly with `HttpClientZioBackend`.

The type of supported streams is `Stream[Throwable, Byte]`. The streams capability is represented as `sttp.client3.impl.zio.ZioStreams`. To leverage ZIO environment, use the `SttpClient` object to create request send effects.

Requests can be sent with a streaming body:

```scala
import sttp.capabilities.zio.ZioStreams
import sttp.client3._
import sttp.client3.httpclient.zio.send
import zio.stream._

val s: Stream[Throwable, Byte] =  ???

val request = basicRequest
  .streamBody(ZioStreams)(s)
  .post(uri"...")

send(request)
```

And receive response bodies as a stream:

```scala
import sttp.capabilities.zio.ZioStreams
import sttp.client3._
import sttp.client3.httpclient.zio.{SttpClient, send}

import zio._
import zio.stream._

import scala.concurrent.duration.Duration

val request =
  basicRequest
    .post(uri"...")
    .response(asStreamUnsafe(ZioStreams))
    .readTimeout(Duration.Inf)

val response: ZIO[SttpClient, Throwable, Response[Either[String, Stream[Throwable, Byte]]]] = send(request)
```

## Websockets

The ZIO backend supports both regular and streaming [websockets](../websockets.md).

## Testing

The ZIO backends also support a ZIO-familiar way of configuring [stubs](../testing.md) as well. In addition to the
usual way of creating a stand-alone stub, you can also define your stubs as effects instead:

```scala
import sttp.client3._
import sttp.model._
import sttp.client3.httpclient._
import sttp.client3.httpclient.zio._
import sttp.client3.httpclient.zio.stubbing._

val stubEffect = for {
  _ <- whenRequestMatches(_.uri.toString.endsWith("c")).thenRespond("c")
  _ <- whenRequestMatchesPartial { case r if r.method == Method.POST => Response.ok("b") }
  _ <- whenAnyRequest.thenRespond("a")
} yield ()

val responseEffect = stubEffect *> send(basicRequest.get(uri"http://example.org/a")).map(_.body)

responseEffect.provideLayer(HttpClientZioBackend.stubLayer) // Task[Either[String, String]]
```

The `whenRequestMatches`, `whenRequestMatchesPartial`, `whenAnyRequest` are effects which require the `SttpClientStubbing`
dependency. They enrich the stub with the given behavior.

Then, the `stubLayer` provides both an implementation of the `SttpClientStubbing` dependency, as well as a `SttpClient`
which is backed by the stub.

## Server-sent events

Received data streams can be parsed to a stream of server-sent events (SSE):

```scala
import zio._
import zio.stream._

import sttp.capabilities.zio.ZioStreams
import sttp.client3.impl.zio.ZioServerSentEvents
import sttp.model.sse.ServerSentEvent
import sttp.client3._

def processEvents(source: Stream[Throwable, ServerSentEvent]): Task[Unit] = ???

basicRequest.response(asStream(ZioStreams)(stream => 
  processEvents(stream.via(ZioServerSentEvents.parse))))
```
