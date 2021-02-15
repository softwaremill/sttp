# Monix backends

There are several backend implementations which are `monix.eval.Task`-based. These backends are **asynchronous**. Sending a request is a non-blocking, lazily-evaluated operation and results in a response wrapped in a `Task`. 

## Using async-http-client

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client3" %% "async-http-client-backend-monix" % "@VERSION@"
```
           
This backend depends on [async-http-client](https://github.com/AsyncHttpClient/async-http-client), uses [Netty](http://netty.io) behind the scenes.

Next you'll need to define a backend instance as an implicit value. This can be done in two basic ways:

* by creating a `Task`, which describes how the backend is created, or instantiating the backend directly. In this case, you'll need to close the backend manually
* by creating a `Resource`, which will instantiate the backend and close it after it has been used

This backend support host header overrides.

A non-comprehensive summary of how the backend can be created is as follows:

```scala mdoc:compile-only
import sttp.client3.asynchttpclient.monix.AsyncHttpClientMonixBackend
import sttp.client3._

AsyncHttpClientMonixBackend().flatMap { backend => ??? }

// or, if you'd like the backend to be wrapped in cats-effect Resource:
AsyncHttpClientMonixBackend.resource().use { backend => ??? }

// or, if you'd like to use custom configuration:
import org.asynchttpclient.AsyncHttpClientConfig
val config: AsyncHttpClientConfig = ???
AsyncHttpClientMonixBackend.usingConfig(config).flatMap { backend => ??? }

// or, if you'd like to use adjust the configuration sttp creates:
import org.asynchttpclient.DefaultAsyncHttpClientConfig
val sttpOptions: SttpBackendOptions = SttpBackendOptions.Default 
val adjustFunction: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder = ???
AsyncHttpClientMonixBackend.usingConfigBuilder(adjustFunction, sttpOptions).flatMap { backend => ??? }

// or, if you'd like to instantiate the AsyncHttpClient yourself:
import org.asynchttpclient.AsyncHttpClient
val asyncHttpClient: AsyncHttpClient = ??? 
val backend = AsyncHttpClientMonixBackend.usingClient(asyncHttpClient)
```

## Using OkHttp

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client3" %% "okhttp-backend-monix" % "@VERSION@"
```

Create the backend using:

```scala mdoc:compile-only
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

This backend supports host header override.

## Using HttpClient (Java 11+)

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "httpclient-backend-monix" % "@VERSION@"
```

Create the backend using:

```scala mdoc:compile-only
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
jdk.httpclient.allowRestrictedHeaders=host
```

## Streaming

The Monix backends support streaming. The streams capability is represented as `sttp.client3.impl.monix.MonixStreams`. The type of supported streams in this case is `Observable[ByteBuffer]`. That is, you can set such an observable as a request body (using the async-http-client backend as an example, but any of the above backends can be used):

```scala mdoc:compile-only
import sttp.capabilities.monix.MonixStreams
import sttp.client3._
import sttp.client3.asynchttpclient.monix._

import monix.reactive.Observable

AsyncHttpClientMonixBackend().flatMap { backend =>
  val obs: Observable[Array[Byte]] =  ???

  basicRequest
    .streamBody(MonixStreams)(obs)
    .post(uri"...")
    .send(backend)
}
```

And receive responses as an observable stream:

```scala mdoc:compile-only
import sttp.capabilities.monix.MonixStreams
import sttp.client3._
import sttp.client3.asynchttpclient.monix._

import monix.eval.Task
import monix.reactive.Observable
import scala.concurrent.duration.Duration

AsyncHttpClientMonixBackend().flatMap { backend =>
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

```scala mdoc:compile-only
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