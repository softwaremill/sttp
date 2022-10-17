# ZIO backends

The [ZIO](https://github.com/zio/zio) backends are **asynchronous**. Sending a request is a non-blocking, lazily-evaluated operation and results in a response wrapped in a `zio.Task`. There's a transitive dependency on the `zio` or `zio1` modules.

The `*-zio` modules depend on ZIO 2.x. For ZIO 1.x support, use modules with the `*-zio1` suffix.

## Using HttpClient

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "zio" % "3.8.3"  // for ZIO 2.x
"com.softwaremill.sttp.client3" %% "zio1" % "3.8.3" // for ZIO 1.x
```

Create the backend using:

```scala
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


## Using Armeria

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "armeria-backend-zio" % "3.8.3"  // for ZIO 2.x
"com.softwaremill.sttp.client3" %% "armeria-backend-zio1" % "3.8.3" // for ZIO 1.x
```

add imports:

```scala
import sttp.client3.armeria.zio.ArmeriaZioBackend
```

create client:

```scala
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

## ZIO layers

As an alternative to effectfully or resourcefully creating backend instances, ZIO layers can be used. In this scenario, the lifecycle of a `SttpBackend` service is described by `ZLayer`s, which can be created using the `.layer`/`.layerUsingConfig`/... methods on `HttpClientZioBackend` / `ArmeriaZioBackend`.

The layers can be used to provide an implementation of the `SttpBackend` dependency when creating services. For example:

```scala
import sttp.client3._
import sttp.client3.httpclient.zio._
import zio._

class MyService(sttpBackend: SttpBackend[Task, Any]) {
  def runLogic(): Task[Response[String]] = {
    val request = basicRequest.response(asStringAlways).get(uri"https://httpbin.org/get")
    sttpBackend.send(request)
  }
}

object MyService {
  val live: ZLayer[SttpBackend[Task, Any], Any, MyService] = ZLayer.fromFunction(new MyService(_))
}

ZLayer.make[MyService](MyService.live, HttpClientZioBackend.layer())
```

## Streaming

The ZIO based backends support streaming using zio-streams. The following example is using the `HttpClientZioBackend`.

The type of supported streams is `Stream[Throwable, Byte]`. The streams capability is represented as `sttp.client3.impl.zio.ZioStreams`. To leverage ZIO environment, use the `SttpClient` object to create request send effects.

Requests can be sent with a streaming body:

```scala
import sttp.capabilities.zio.ZioStreams
import sttp.client3._
import zio.stream._
import zio.Task

val sttpBackend: SttpBackend[Task, ZioStreams] = ???
val s: Stream[Throwable, Byte] =  ???

val request = basicRequest
  .streamBody(ZioStreams)(s)
  .post(uri"...")

sttpBackend.send(request)
```

And receive response bodies as a stream:

```scala
import sttp.capabilities.zio.ZioStreams
import sttp.client3._

import zio._
import zio.stream._

import scala.concurrent.duration.Duration

val sttpBackend: SttpBackend[Task, ZioStreams] = ???

val request =
  basicRequest
    .post(uri"...")
    .response(asStreamUnsafe(ZioStreams))
    .readTimeout(Duration.Inf)

val response: ZIO[Any, Throwable, Response[Either[String, Stream[Throwable, Byte]]]] = sttpBackend.send(request)
```

## Websockets

The `HttpClient` ZIO backend supports both regular and streaming [websockets](../websockets.md).

## Testing

A stub backend can be created through the `.stub` method on the companion object, and configured as described in the
[testing](../testing.md) section. 

A layer with the stub `SttpBackend` can be then created by simply calling `ZLayer.succeed(sttpBackendStub)`. 

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
  processEvents(stream.viaFunction(ZioServerSentEvents.parse))))
```
