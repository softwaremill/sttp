# ZIO backends

The [ZIO](https://github.com/zio/zio) backends are **asynchronous**. Sending a request is a non-blocking, lazily-evaluated operation and results in a response wrapped in a `zio.Task`. There's a transitive dependency on the `zio` or `zio1` modules.

The `*-zio` modules depend on ZIO 2.x. For ZIO 1.x support, use modules with the `*-zio1` suffix.

## Using HttpClient

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client4" %% "zio" % "@VERSION@"  // for ZIO 2.x
"com.softwaremill.sttp.client4" %% "zio1" % "@VERSION@" // for ZIO 1.x
```

Create the backend using:

```scala mdoc:compile-only
import sttp.client4.httpclient.zio.HttpClientZioBackend

HttpClientZioBackend().flatMap { backend => ??? }

// or, if you'd like the backend to be created in a Scope:
HttpClientZioBackend.scoped().flatMap { backend => ??? }

// or, if you'd like to instantiate the HttpClient yourself:
import java.net.http.HttpClient
val httpClient: HttpClient = ???
val backend = HttpClientZioBackend.usingClient(httpClient)

// or, obtain a Scope with a custom instance of the HttpClient:
HttpClientZioBackend.scopedUsingClient(httpClient).flatMap { backend => ??? }
```

This backend is based on the built-in `java.net.http.HttpClient` available from Java 11 onwards. The backend is fully non-blocking, with back-pressured websockets.

Host header override is supported in environments running Java 12 onwards, but it has to be enabled by system property:

```
-Djdk.httpclient.allowRestrictedHeaders=host
```

## Using Armeria

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client4" %% "armeria-backend-zio" % "@VERSION@"  // for ZIO 2.x
"com.softwaremill.sttp.client4" %% "armeria-backend-zio1" % "@VERSION@" // for ZIO 1.x
```

add imports:

```scala mdoc:silent
import sttp.client4.armeria.zio.ArmeriaZioBackend
```

create client:

```scala mdoc:compile-only
ArmeriaZioBackend().flatMap { backend => ??? }

// or, if you'd like the backend to be wrapped in a Scope:
ArmeriaZioBackend.scoped().flatMap { backend => ??? }

// You can use the default client which reuses the connection pool of ClientFactory.ofDefault()
ArmeriaZioBackend.usingDefaultClient().flatMap { backend => ??? }
```

```{eval-rst}
.. note:: The default client factory is reused to create `ArmeriaZioBackend` if a `SttpBackendOptions` is unspecified. So you only need to manage a resource when `SttpBackendOptions` is used.
```

or, if you'd like to instantiate the [WebClient](https://armeria.dev/docs/client-http) yourself:

```scala mdoc:compile-only
import com.linecorp.armeria.client.circuitbreaker.*
import com.linecorp.armeria.client.WebClient

// Fluently build Armeria WebClient with built-in decorators
val client = WebClient.builder("https://my-service.com")
             // Open circuit on 5xx server error status
             .decorator(CircuitBreakerClient.newDecorator(CircuitBreaker.ofDefaultName(),
               CircuitBreakerRule.onServerErrorStatus()))
             .build()

ArmeriaZioBackend.usingClient(client).flatMap { backend => ??? }
```

```{eval-rst}
.. note:: A WebClient could fail to follow redirects if the WebClient is created with a base URI and a redirect location is a different URI.
```

This backend is build on top of [Armeria](https://armeria.dev/docs/client-http).
Armeria's [ClientFactory](https://armeria.dev/docs/client-factory) manages connections and protocol-specific properties.
Please visit [the official documentation](https://armeria.dev/docs/client-factory) to learn how to configure it.

## Using JavaScript

The ZIO backend is also available for the JS platform, see [the `FetchBackend` documentation](javascript/fetch.md).
The `FetchBackend` companion object contains methods to create the backend directly, as a layer or scoped.

## ZIO layers + constructors

When using constructors to express service dependencies, ZIO layers can be used to provide the `SttpBackend` instance, instead of creating one by hand. In this scenario, the lifecycle of a `SttpBackend` service is described by `ZLayer`s, which can be created using the `.layer`/`.layerUsingConfig`/... methods on `HttpClientZioBackend` / `ArmeriaZioBackend`.

The layers can be used to provide an implementation of the `SttpBackend` dependency when creating services. For example:

```scala mdoc:compile-only
import sttp.client4.*
import sttp.client4.httpclient.zio.*
import zio.*

class MyService(sttpBackend: Backend[Task]) {
  def runLogic(): Task[Response[String]] = {
    val request = basicRequest.response(asStringAlways).get(uri"https://httpbin.org/get")
    request.send(sttpBackend)
  }
}

object MyService {
  val live: ZLayer[Backend[Task], Any, MyService] = ZLayer.fromFunction(new MyService(_))
}

ZLayer.make[MyService](MyService.live, HttpClientZioBackend.layer())
```

## ZIO environment

As yet another alternative to effectfully or resourcefully creating backend instances, ZIO environment can be used. There are top-level `send` and `sendR` top-level methods which require a `SttpClient` to be available in the environment. The `SttpClient` itself is a type alias:

 ```scala
 package sttp.client4.httpclient.zio
 type SttpClient = SttpBackend[Task, ZioStreams with WebSockets]

 // or, when using Armeria
 package sttp.client4.armeria.zio
 type SttpClient = SttpBackend[Task, ZioStreams]
 ```

The lifecycle of the `SttpClient` service is described by `ZLayer`s, which can be created using the `.layer`/`.layerUsingConfig`/... methods on `HttpClientZioBackend` / `ArmeriaZioBackend`.

The `SttpClient` companion object contains effect descriptions which use the `SttpClient` service from the environment to send requests or open websockets. This is different from sttp usage with other effect libraries (which require invoking `.send(backend)` on the request), but is more in line with one of the styles of using ZIO. For example:

 ```scala mdoc:compile-only
 import sttp.client4.*
 import sttp.client4.httpclient.zio.*
 import zio.*

 val request = basicRequest.get(uri"https://httpbin.org/get")
 val sent: ZIO[SttpClient, Throwable, Response[Either[String, String]]] = 
   send(request)
 ```

## Streaming

The ZIO based backends support streaming using zio-streams. The following example is using the `HttpClientZioBackend`.

The type of supported streams is `Stream[Throwable, Byte]`. The streams capability is represented as `sttp.client4.impl.zio.ZioStreams`. To leverage ZIO environment, use the `SttpClient` object to create request send effects.

Requests can be sent with a streaming body:

```scala mdoc:compile-only
import sttp.capabilities.zio.ZioStreams
import sttp.client4.*
import zio.stream.*
import zio.Task

val sttpBackend: StreamBackend[Task, ZioStreams] = ???
val s: Stream[Throwable, Byte] =  ???

val request = basicRequest
  .post(uri"...")
  .streamBody(ZioStreams)(s)

request.send(sttpBackend)
```

And receive response bodies as a stream:

```scala mdoc:compile-only
import sttp.capabilities.zio.ZioStreams
import sttp.client4.*

import zio.*
import zio.stream.*

import scala.concurrent.duration.Duration

val sttpBackend: StreamBackend[Task, ZioStreams] = ???

val request =
  basicRequest
    .post(uri"...")
    .response(asStreamUnsafe(ZioStreams))
    .readTimeout(Duration.Inf)

val response: ZIO[Any, Throwable, Response[Either[String, Stream[Throwable, Byte]]]] = request.send(sttpBackend)
```

## Websockets

The `HttpClient` ZIO backend supports both regular and streaming [websockets](../other/websockets.md).

## Testing

A stub backend can be created through the `.stub` method on the companion object, and configured as described in the
[testing](../testing.md) section. 

A layer with the stub `SttpBackend` can be then created by simply calling `ZLayer.succeed(sttpBackendStub)`. 

## Server-sent events

Received data streams can be parsed to a stream of server-sent events (SSE):

```scala mdoc:compile-only
import zio.*
import zio.stream.*

import sttp.capabilities.zio.ZioStreams
import sttp.client4.impl.zio.ZioServerSentEvents
import sttp.model.sse.ServerSentEvent
import sttp.client4.*

def processEvents(source: Stream[Throwable, ServerSentEvent]): Task[Unit] = ???

basicRequest
  .get(uri"...")
  .response(asStream(ZioStreams)(stream => processEvents(stream.viaFunction(ZioServerSentEvents.parse))))
```
