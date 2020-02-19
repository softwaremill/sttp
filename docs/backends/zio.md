# ZIO backends

The [ZIO](https://github.com/zio/zio) backends are **asynchronous**. Sending a request is a non-blocking, lazily-evaluated operation and results in a response wrapped in a `zio.Task`. There's a transitive dependency on `zio`.

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % "2.0.0-RC11"
```
           
This backend depends on [async-http-client](https://github.com/AsyncHttpClient/async-http-client), uses [Netty](http://netty.io) behind the scenes and supports effect cancellation.

Next you'll need to add an implicit value:

```scala
import sttp.client.asynchttpclient.zio.AsyncHttpClientZioBackend

AsyncHttpClientZioBackend().flatMap { implicit backend => ... }

// or, if you'd like to use custom configuration:
AsyncHttpClientZioBackend.usingConfig(asyncHttpClientConfig).flatMap { implicit backend => ... }

// or, if you'd like to use adjust the configuration sttp creates:
AsyncHttpClientZioBackend.usingConfigBuilder(adjustFunction, sttpOptions).flatMap { implicit backend => ... }

// or, if you'd like to instantiate the AsyncHttpClient yourself:
implicit val sttpBackend = AsyncHttpClientZioBackend.usingClient(asyncHttpClient)
```

## Streaming

To use streaming using zio-streams, add the following dependency instead:

```scala
"com.softwaremill.sttp.client" %% "async-http-client-backend-zio-streams" % "2.0.0-RC11"
```

And use the `sttp.client.asynchttpclient.ziostreams.AsyncHttpClientZioStreamsBackend` backend implementation. The backend supports streaming of type `Stream[Throwable, ByteBuffer]`.

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

See [websockets](../websockets.html) for details on how to use the high-level and low-level interfaces.
