# ZIO backends

The [ZIO](https://github.com/zio/zio) backends are **asynchronous**. Sending a request is a non-blocking, lazily-evaluated operation and results in a response wrapped in a `zio.Task`. There's a transitive dependency on `zio`.

## Using async-http-client

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % "2.2.0"
```
           
This backend depends on [async-http-client](https://github.com/AsyncHttpClient/async-http-client), uses [Netty](http://netty.io) behind the scenes and supports effect cancellation. This backend works with all Scala versions. A Dotty build is available as well.

Next you'll need to define a backend instance as an implicit value. This can be done in two basic ways:

* by creating a `Task` which describes how the backend is created, or instantiating the backend directly. In this case, you'll need to close the backend manually
* by creating a `TaskManaged`, which will instantiate the backend and close it after it has been used

A non-comprehensive summary of how the backend can be created is as follows:

```scala
import sttp.client.asynchttpclient.zio.AsyncHttpClientZioBackend

AsyncHttpClientZioBackend().flatMap { implicit backend => ... }

// or, if you'd like the backend to be wrapped in a Managed:
AsyncHttpClientZioBackend.managed().use { implicit backend => ... }

// or, if you'd like to use custom configuration:
AsyncHttpClientZioBackend.usingConfig(asyncHttpClientConfig).flatMap { implicit backend => ... }

// or, if you'd like to use adjust the configuration sttp creates:
AsyncHttpClientZioBackend.usingConfigBuilder(adjustFunction, sttpOptions).flatMap { implicit backend => ... }

// or, if you'd like to instantiate the AsyncHttpClient yourself:
implicit val sttpBackend = AsyncHttpClientZioBackend.usingClient(asyncHttpClient)
```

## Using HttpClient (Java 11+)

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client" %% "httpclient-backend-zio" % "2.2.0"
```

Create the backend using:

```scala
import sttp.client.httpclient.zio.HttpClientZioBackend

HttpClientZioBackend().flatMap { implicit backend => ... }

// or, if you'd like the backend to be wrapped in a Managed:
HttpClientZioBackend.managed().use { implicit backend => ... }

// or, if you'd like to instantiate the HttpClient yourself:
implicit val sttpBackend = HttpClientZioBackend.usingClient(httpClient)
```

This backend is based on the built-in `java.net.http.HttpClient` available from Java 11 onwards.

## ZIO environment

As an alternative to effectfully or resourcefully creating backend instances, ZIO environment can be used. In this case, a type alias is provided for the service definition:

```scala
package sttp.client.asynchttpclient.zio
type SttpClient = Has[SttpBackend[Task, Stream[Throwable, Byte], WebSocketHandler]]

// or, when using Java 11 & HttpClient

package sttp.client.httpclient.zio
type SttpClient = Has[SttpBackend[BlockingTask, ZStream[Blocking, Throwable, Byte], NothingT]]
```

The lifecycle of the `SttpClient` service is described by `ZLayer`s, which can be created using the `.layer`/`.layerUsingConfig`/... methods on `AsyncHttpClientZioBackend` / `HttpClientZioBackend`.

The `SttpClient` companion object contains effect descriptions which use the `SttpClient` service from the environment to send requests or open websockets. This is different from sttp usage with other effect libraries (which use an implicit backend when `.send()`/`.openWebsocket()` is invoked on the request), but is more in line with how other ZIO services work. For example:

```scala
val request = basicRequest.get(uri"https://httpbin.org/get")

val send: ZIO[SttpClient, Throwable, Response[Either[String, String]]] = 
  SttpClient.send(request)
```

Example using websockets:

```scala
val request = basicRequest.get(uri"wss://echo.websocket.org")

val open: ZIO[SttpClient, Throwable, WebSocketResponse[WebSocket[Task]]] = 
  SttpClient.openWebsocket(request)
```

## Streaming

The ZIO based backends support streaming using zio-streams. The following example is using the `AsyncHttpClientZioBackend` backend, but works similarly with `HttpClientZioBackend`.

The type of supported streams is `Stream[Throwable, Byte]`. To leverage ZIO environment, use the `SttpClient` object to create request send/websocket open effects.

Requests can be sent with a streaming body:

```scala
import sttp.client._
import sttp.client.asynchttpclient.zio._

import zio._
import zio.stream._

val s: Stream[Throwable, Byte] =  ...

val request = basicRequest
  .streamBody(s)
  .post(uri"...")

SttpClient.send(request)
```

And receive response bodies as a stream:

```scala
import sttp.client._
import sttp.client.asynchttpclient.zio._

import zio._
import zio.stream._

import scala.concurrent.duration.Duration

val request =
  basicRequest
    .post(uri"...")
    .response(asStream[Stream[Throwable, Byte]])
    .readTimeout(Duration.Inf)

val response: ZIO[SttpClient, Throwable, Response[Either[String, Stream[Throwable, Byte]]]] = SttpClient.send(request)
```

## Websockets

The ZIO backend supports:

* high-level, "functional" websocket interface, through the `sttp.client.asynchttpclient.zio.ZioWebSocketHandler`
* low-level interface by wrapping a low-level Java interface, `sttp.client.asynchttpclient.WebSocketHandler`

See [websockets](../websockets.md) for details on how to use the high-level and low-level interfaces. Websockets
opened using the `SttpClient.openWebsocket` and `SttpStreamsClient.openWebsocket` (leveraging ZIO environment) always
use the high-level interface.
