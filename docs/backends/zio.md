# ZIO backends

The [ZIO](https://github.com/zio/zio) backends are **asynchronous**. Sending a request is a non-blocking, lazily-evaluated operation and results in a response wrapped in a `zio.Task`. There's a transitive dependency on `zio`.

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % "2.1.0-RC1"
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

## ZIO environment

As an alternative, ZIO environment can be used. In this case, a type alias is provided for the service definition (a streaming version is also available when using the streaming backend in the `ziostreams` package):

```scala
package sttp.client.asynchttpclient.zio

type SttpClient = Has[SttpBackend[Task, Nothing, WebSocketHandler]]
```

The lifecycle of the `SttpClient` service is described by `ZLayer`s, which can be created using the `.layer`/`.layerUsingConfig`/... methods on `AsyncHttpClientZioBackend`.

The `SttpClient` companion object contains effect descriptions which use the `SttpClient` service from the environment to send requests or open websockets. This is different from sttp usage with other effect libraries (which use an implicit backend when `.send()`/`.openWebsocket()` is invoked on the request), but is more in line with how other ZIO services work. For example:

```scala
val request = basicRequest.get(uri"https://httpbin.org/get")

val send: ZIO[SttpClient, Throwable, Response[Either[String, String]]] = SttpClient.send(request)
```

Example using websockets:

```scala
val request = basicRequest.get(uri"wss://echo.websocket.org")

val open: ZIO[SttpClient, Throwable, WebSocketResponse[WebSocket[Task]]] = SttpClient.openWebsocket(request)
```

## Streaming

To use streaming using zio-streams, add the following dependency instead:

```scala
"com.softwaremill.sttp.client" %% "async-http-client-backend-zio-streams" % "2.1.0-RC1"
```

And use the `sttp.client.asynchttpclient.ziostreams.AsyncHttpClientZioStreamsBackend` backend implementation. The backend supports streaming of type `Stream[Throwable, ByteBuffer]`. To leverage ZIO environment, use the `SttpStreamsClient` object to create request send/websocket open effects.

Requests can be sent with a streaming body:

```scala
import sttp.client._
import sttp.client.asynchttpclient.ziostreams._

import java.nio.ByteBuffer

import zio._
import zio.stream._

AsyncHttpClientZioStreamsBackend().flatMap { implicit backend =>
  val s: Stream[Throwable, ByteBuffer] =  ...

  basicRequest
    .streamBody(s)
    .post(uri"...")
}
```

And receive response bodies as a stream:

```scala
import sttp.client._
import sttp.client.asynchttpclient.ziostreams._

import java.nio.ByteBuffer

import zio._
import zio.stream._

import scala.concurrent.duration.Duration

AsyncHttpClientZioStreamsBackend().flatMap { implicit backend =>
  val response: Task[Response[Either[String, Stream[Throwable, ByteBuffer]]]] =
    basicRequest
      .post(uri"...")
      .response(asStream[Stream[Throwable, ByteBuffer]])
      .readTimeout(Duration.Inf)
      .send()
}
```

## Websockets

The ZIO backend supports:

* high-level, "functional" websocket interface, through the `sttp.client.asynchttpclient.zio.ZioWebSocketHandler`
* low-level interface by wrapping a low-level Java interface, `sttp.client.asynchttpclient.WebSocketHandler`

See [websockets](../websockets.html) for details on how to use the high-level and low-level interfaces. Websockets
opened using the `SttpClient.openWebsocket` and `SttpStreamsClient.openWebsocket` (leveraging ZIO environment) always
use the high-level interface.
