# Monix backends

There are several backend implementations which are `monix.eval.Task`-based. These backends are **asynchronous**. Sending a request is a non-blocking, lazily-evaluated operation and results in a response wrapped in a `Task`. 

## Using HttpClient

Creation of the backend can be done in two basic ways:

* by creating a `Task`, which describes how the backend is created, or instantiating the backend directly. In this case, you'll need to close the backend manually.
* by creating a `Resource`, which will instantiate the backend and close it after it has been used.

Firstly, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "monix" % "3.8.3"
```

and create the backend using:

```scala
import sttp.client3.httpclient.monix.HttpClientMonixBackend

HttpClientMonixBackend().flatMap { backend => ??? }

// or, if you'd like the backend to be wrapped in cats-effect Resource:
HttpClientMonixBackend.resource().use { backend => ??? }

// or, if you'd like to instantiate the HttpClient yourself:
import java.net.http.HttpClient
val httpClient: HttpClient = ???
val backend = HttpClientMonixBackend.usingClient(httpClient)
```

This backend is based on the built-in `java.net.http.HttpClient` available from Java 11 onwards.

Host header override is supported in environments running Java 12 onwards, but it has to be enabled by system property:

```
-Djdk.httpclient.allowRestrictedHeaders=host
```


## Using OkHttp

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client3" %% "okhttp-backend-monix" % "3.8.3"
```

Create the backend using:

```scala
import sttp.client3.okhttp.monix.OkHttpMonixBackend

OkHttpMonixBackend().flatMap { backend => ??? }

// or, if you'd like the backend to be wrapped in cats-effect Resource:
OkHttpMonixBackend.resource().use { backend => ??? }

// or, if you'd like to instantiate the OkHttpClient yourself:
import okhttp3._
val okHttpClient: OkHttpClient = ???
val backend = OkHttpMonixBackend.usingClient(okHttpClient)
```

This backend depends on [OkHttp](http://square.github.io/okhttp/) and fully supports HTTP/2.

## Using Armeria

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "armeria-backend-monix" % "3.8.3"
```

add imports:

```scala
import sttp.client3.armeria.monix.ArmeriaMonixBackend
```

create client:

```scala
import monix.execution.Scheduler.Implicits.global
val backend = ArmeriaMonixBackend()

// You can use the default client which reuses the connection pool of ClientFactory.ofDefault()
ArmeriaMonixBackend.usingDefaultClient()
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
             
val backend = ArmeriaMonixBackend.usingClient(client)
```

```eval_rst
.. note:: A WebClient could fail to follow redirects if the WebClient is created with a base URI and a redirect location is a different URI.
```

This backend is build on top of [Armeria](https://armeria.dev/docs/client-http).
Armeria's [ClientFactory](https://armeria.dev/docs/client-factory) manages connections and protocol-specific properties.
Please visit [the official documentation](https://armeria.dev/docs/client-factory) to learn how to configure it.

## Streaming

The Monix backends support streaming. The streams capability is represented as `sttp.client3.impl.monix.MonixStreams`. The type of supported streams in this case is `Observable[ByteBuffer]`. That is, you can set such an observable as a request body (using the http-client backend as an example, but any of the above backends can be used):

```scala
import sttp.capabilities.monix.MonixStreams
import sttp.client3._
import sttp.client3.httpclient.monix.HttpClientMonixBackend

import monix.reactive.Observable

HttpClientMonixBackend().flatMap { backend =>
  val obs: Observable[Array[Byte]] =  ???

  basicRequest
    .streamBody(MonixStreams)(obs)
    .post(uri"...")
    .send(backend)
}
```

And receive responses as an observable stream:

```scala
import sttp.capabilities.monix.MonixStreams
import sttp.client3._
import sttp.client3.httpclient.monix.HttpClientMonixBackend

import monix.eval.Task
import monix.reactive.Observable
import scala.concurrent.duration.Duration

HttpClientMonixBackend().flatMap { backend =>
  val response: Task[Response[Either[String, Observable[Array[Byte]]]]] =
    basicRequest
      .post(uri"...")
      .response(asStreamUnsafe(MonixStreams))
      .readTimeout(Duration.Inf)
      .send(backend)
    response
}
```

## Websockets

The Monix backend supports both regular and streaming [websockets](../websockets.md).

## Server-sent events

Received data streams can be parsed to a stream of server-sent events (SSE):

```scala
import monix.reactive.Observable
import monix.eval.Task

import sttp.capabilities.monix.MonixStreams
import sttp.client3.impl.monix.MonixServerSentEvents
import sttp.model.sse.ServerSentEvent
import sttp.client3._

def processEvents(source: Observable[ServerSentEvent]): Task[Unit] = ???

basicRequest.response(asStream(MonixStreams)(stream => 
  processEvents(stream.transform(MonixServerSentEvents.parse))))
```
