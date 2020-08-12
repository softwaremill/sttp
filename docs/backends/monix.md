# Monix backends

There are several backend implementations which are `monix.eval.Task`-based. These backends are **asynchronous**. Sending a request is a non-blocking, lazily-evaluated operation and results in a response wrapped in a `Task`. 

## Using async-http-client

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client" %% "async-http-client-backend-monix" % "@VERSION@"
```
           
This backend depends on [async-http-client](https://github.com/AsyncHttpClient/async-http-client), uses [Netty](http://netty.io) behind the scenes and supports effect cancellation.

Next you'll need to define a backend instance as an implicit value. This can be done in two basic ways:

* by creating a `Task`, which describes how the backend is created, or instantiating the backend directly. In this case, you'll need to close the backend manually
* by creating a `Resource`, which will instantiate the backend and close it after it has been used

A non-comprehensive summary of how the backend can be created is as follows:

```scala mdoc:compile-only
import sttp.client.asynchttpclient.monix.AsyncHttpClientMonixBackend
import sttp.client._

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
"com.softwaremill.sttp.client" %% "okhttp-backend-monix" % "@VERSION@"
```

Create the backend using:

```scala mdoc:compile-only
import sttp.client.okhttp.monix.OkHttpMonixBackend

OkHttpMonixBackend().flatMap { backend => ??? }

// or, if you'd like the backend to be wrapped in cats-effect Resource:
OkHttpMonixBackend.resource().use { backend => ??? }

// or, if you'd like to instantiate the OkHttpClient yourself:
import okhttp3._
val okHttpClient: OkHttpClient = ???
val backend = OkHttpMonixBackend.usingClient(okHttpClient)
```

This backend depends on [OkHttp](http://square.github.io/okhttp/) and fully supports HTTP/2.

## Using HttpClient (Java 11+)

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client" %% "httpclient-backend-monix" % "@VERSION@"
```

Create the backend using:

```scala mdoc:compile-only
import sttp.client.httpclient.monix.HttpClientMonixBackend

HttpClientMonixBackend().flatMap { backend => ??? }

// or, if you'd like the backend to be wrapped in cats-effect Resource:
HttpClientMonixBackend.resource().use { backend => ??? }

// or, if you'd like to instantiate the HttpClient yourself:
import java.net.http.HttpClient
val httpClient: HttpClient = ???
val backend = HttpClientMonixBackend.usingClient(httpClient)
```

This backend is based on the built-in `java.net.http.HttpClient` available from Java 11 onwards.

## Streaming

The Monix backends support streaming. The streams capability is represented as `sttp.client.impl.monix.MonixStreams`. The type of supported streams in this case is `Observable[ByteBuffer]`. That is, you can set such an observable as a request body (using the async-http-client backend as an example, but any of the above backends can be used):

```scala mdoc:compile-only
import sttp.client._
import sttp.client.asynchttpclient.monix._
import sttp.client.impl.monix.MonixStreams

import java.nio.ByteBuffer
import monix.reactive.Observable

AsyncHttpClientMonixBackend().flatMap { backend =>
  val obs: Observable[ByteBuffer] =  ???

  basicRequest
    .streamBody(MonixStreams)(obs)
    .post(uri"...")
    .send(backend)
}
```

And receive responses as an observable stream:

```scala mdoc:compile-only
import sttp.client._
import sttp.client.asynchttpclient.monix._
import sttp.client.impl.monix.MonixStreams

import java.nio.ByteBuffer
import monix.eval.Task
import monix.reactive.Observable
import scala.concurrent.duration.Duration

AsyncHttpClientMonixBackend().flatMap { backend =>
  val response: Task[Response[Either[String, Observable[ByteBuffer]]]] =
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
