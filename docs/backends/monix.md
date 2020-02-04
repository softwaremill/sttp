# Monix backends

There are several backend implementations which are `monix.eval.Task`-based. These backends are **asynchronous**. Sending a request is a non-blocking, lazily-evaluated operation and results in a response wrapped in a `Task`. 

## Using async-http-client

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client" %% "async-http-client-backend-monix" % "2.0.0-RC8"
```
           
This backend depends on [async-http-client](https://github.com/AsyncHttpClient/async-http-client) and uses [Netty](http://netty.io) behind the scenes.

Next you'll need to add an implicit value:

```scala
import sttp.client.asynchttpclient.monix.AsyncHttpClientMonixBackend

AsyncHttpClientMonixBackend().flatMap { implicit backend => ... }

// or, if you'd like the backend to be wrapped in cats-effect Resource:
AsyncHttpClientMonixBackend.resource().use { implicit backend => ... }

// or, if you'd like to use custom configuration:
AsyncHttpClientMonixBackend.usingConfig(asyncHttpClientConfig).flatMap { implicit backend => ... }

// or, if you'd like to use adjust the configuration sttp creates:
AsyncHttpClientMonixBackend.usingConfigBuilder(adjustFunction, sttpOptions).flatMap { implicit backend => ... }

// or, if you'd like to instantiate the AsyncHttpClient yourself:
implicit val sttpBackend = AsyncHttpClientFutureBackend.usingClient(asyncHttpClient)
```

## Using OkHttp

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client" %% "okhttp-backend-monix" % "2.0.0-RC8"
```

Create the backend using:

```scala
import sttp.client.okhttp.monix.OkHttpMonixBackend

OkHttpMonixBackend().flatMap { implicit backend => ... }

// or, if you'd like the backend to be wrapped in cats-effect Resource:
OkHttpMonixBackend.resource().use { implicit backend => ... }

// or, if you'd like to instantiate the OkHttpClient yourself:
implicit val sttpBackend = OkHttpMonixBackend.usingClient(okHttpClient)
```

This backend depends on [OkHttp](http://square.github.io/okhttp/) and fully supports HTTP/2.

## Using HttpClient (Java 11+)

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client" %% "httpclient-backend-monix" % "2.0.0-RC8"
```

Create the backend using:

```scala
import sttp.client.httpclient.monix.HttpClientMonixBackend

HttpClientMonixBackend().flatMap { implicit backend => ... }

// or, if you'd like the backend to be wrapped in cats-effect Resource:
HttpClientMonixBackend.resource().use { implicit backend => ... }

// or, if you'd like to instantiate the HttpClient yourself:
implicit val sttpBackend = HttpClientMonixBackend.usingClient(asyncHttpClient)
```

This backend is based on the built-in `java.net.http.HttpClient` available from Java 11 onwards.

## Streaming

The Monix backends support streaming. The type of supported streams in this case is `Observable[ByteBuffer]`. That is, you can set such an observable as a request body (using the async-http-client backend as an example, but any of the above backends can be used):

```scala
import sttp.client._
import sttp.client.asynchttpclient.monix._

import java.nio.ByteBuffer
import monix.reactive.Observable

AsyncHttpClientMonixBackend().flatMap { implicit backend =>
  val obs: Observable[ByteBuffer] =  ...

  basicRequest
    .streamBody(obs)
    .post(uri"...")
}
```

And receive responses as an observable stream:

```scala
import sttp.client._
import sttp.client.asynchttpclient.monix._

import java.nio.ByteBuffer
import monix.eval.Task
import monix.reactive.Observable
import scala.concurrent.duration.Duration

AsyncHttpClientMonixBackend().flatMap { implicit backend =>
  val response: Task[Response[Either[String, Observable[ByteBuffer]]]] =
    basicRequest
      .post(uri"...")
      .response(asStream[Observable[ByteBuffer]])
      .readTimeout(Duration.Inf)
      .send()
}
```

## Websockets

The Monix backend supports:

* high-level, "functional" websocket interface, through the `sttp.client.asynchttpclient.monix.MonixWebSocketHandler`
* low-level interface by wrapping a low-level Java interface, `sttp.client.asynchttpclient.WebSocketHandler`

See [websockets](../websockets.html) for details on how to use the high-level and low-level interfaces.
