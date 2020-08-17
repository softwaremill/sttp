# fs2 backend

The [fs2](https://github.com/functional-streams-for-scala/fs2) backend is **asynchronous**. It can be created for any type implementing the `cats.effect.Async` typeclass, such as `cats.effect.IO`. Sending a request is a non-blocking, lazily-evaluated operation and results in a wrapped response. There's a transitive dependency on `cats-effect`. 

## Using async-http-client

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client" %% "async-http-client-backend-fs2" % "2.2.4"
```

And some imports:

```scala
import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import cats.effect._
import sttp.client._

// an implicit `cats.effect.ContextShift` in required to create an instance of `cats.effect.Concurrent`
// for `cats.effect.IO`:
implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
```
           
This backend depends on [async-http-client](https://github.com/AsyncHttpClient/async-http-client) and uses [Netty](http://netty.io) behind the scenes.

Next you'll need to define a backend instance as an implicit value. This can be done in two basic ways:

* by creating an effect, which describes how a backend is created, or instantiating the backend directly. In this case, you'll need to close the backend manually
* by creating a `Resource`, which will instantiate the backend and close it after it has been used

A non-comprehensive summary of how the backend can be created is as follows:

```scala
AsyncHttpClientFs2Backend[IO]().flatMap { backend => ??? }
```

or, if you'd like to use a custom configuration:

```scala
import org.asynchttpclient.AsyncHttpClientConfig

val config: AsyncHttpClientConfig = ???
AsyncHttpClientFs2Backend.usingConfig[IO](config).flatMap { backend => ??? }
```

or, if you'd like to use adjust the configuration sttp creates:

```scala
import org.asynchttpclient.DefaultAsyncHttpClientConfig

val sttpOptions: SttpBackendOptions = SttpBackendOptions.Default  
val adjustFunction: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder = ???
AsyncHttpClientFs2Backend.usingConfigBuilder[IO](adjustFunction, sttpOptions).flatMap { backend => ??? }
```

or, if you'd like the backend to be wrapped in cats-effect Resource:

```scala
AsyncHttpClientFs2Backend.resource[IO]().use { backend => ??? }
```

or, if you'd like to instantiate the AsyncHttpClient yourself:

```scala
import org.asynchttpclient.AsyncHttpClient

val asyncHttpClient: AsyncHttpClient = ???  
val backend = AsyncHttpClientFs2Backend.usingClient[IO](asyncHttpClient)
```

## Using HttpClient (Java 11+)

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client" %% "httpclient-backend-fs2" % "2.2.4"
```

And some imports:

```scala
import sttp.client.httpclient.fs2.HttpClientFs2Backend
import cats.effect._
import sttp.client._

// an implicit `cats.effect.ContextShift` is required to create a concurrent instance for `cats.effect.IO`,
// as well as a `cats.effect.Blocker` instance. Note that you'll probably want to use a different thread
// pool for blocking.
implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
val blocker = Blocker.liftExecutionContext(scala.concurrent.ExecutionContext.global)
```

Create the backend using:

```scala
import sttp.client.httpclient.fs2.HttpClientFs2Backend
HttpClientFs2Backend[IO](blocker).flatMap { backend => ??? }
```

or, if you'd like the backend to be wrapped in cats-effect Resource:

```scala
HttpClientFs2Backend.resource[IO](blocker).use { backend => ??? }
```

or, if you'd like to instantiate the HttpClient yourself:

```scala
import java.net.http.HttpClient
val httpClient: HttpClient = ???
val backend = HttpClientFs2Backend.usingClient[IO](httpClient, blocker)
```

This backend is based on the built-in `java.net.http.HttpClient` available from Java 11 onwards.

## Streaming

The fs2 backend supports streaming for any instance of the `cats.effect.Effect` typeclass, such as `cats.effect.IO`. If `IO` is used then the type of supported streams is `fs2.Stream[IO, Byte]`. The streams capability is represented as `sttp.client.fs2.Fs2Streams`.

Requests can be sent with a streaming body like this:

```scala
import sttp.capabilities.fs2.Fs2Streams
import sttp.client._
import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import fs2.Stream

val effect = AsyncHttpClientFs2Backend[IO]().flatMap { backend =>
  val stream: Stream[IO, Byte] = ???

  basicRequest
    .streamBody(Fs2Streams[IO])(stream)
    .post(uri"...")
    .send(backend)
}
```

Responses can also be streamed:

```scala
import sttp.capabilities.fs2.Fs2Streams
import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend
import fs2.Stream
import scala.concurrent.duration.Duration

val effect = AsyncHttpClientFs2Backend[IO]().flatMap { backend =>
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
