# async-http-client backend

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client" %% "async-http-client-backend-future" % "2.0.0-RC6"
// or
"com.softwaremill.sttp.client" %% "async-http-client-backend-scalaz" % "2.0.0-RC6"
// or
"com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % "2.0.0-RC6"
// or
"com.softwaremill.sttp.client" %% "async-http-client-backend-zio-streams" % "2.0.0-RC6"
// or
"com.softwaremill.sttp.client" %% "async-http-client-backend-monix" % "2.0.0-RC6"
// or
"com.softwaremill.sttp.client" %% "async-http-client-backend-cats" % "2.0.0-RC6"
// or
"com.softwaremill.sttp.client" %% "async-http-client-backend-fs2" % "2.0.0-RC6"
```
           
This backend depends on [async-http-client](https://github.com/AsyncHttpClient/async-http-client). A fully **asynchronous** backend, which uses [Netty](http://netty.io) behind the scenes.

The responses are wrapped depending on the dependency chosen in either a:

* standard Scala `Future`
* [Scalaz](https://github.com/scalaz/scalaz) `Task`. There's a transitive dependency on `scalaz-concurrent`.
* [ZIO](https://github.com/zio/zio) `IO`. There's a transitive dependency on `zio`.
* [Monix](https://monix.io) `Task`. There's a transitive dependency on `monix-eval`.
* Any type implementing the [Cats Effect](https://github.com/typelevel/cats-effect) `Async` typeclass, such as `cats.effect.IO`. There's a transitive dependency on `cats-effect`.
* [fs2](https://github.com/functional-streams-for-scala/fs2) `Stream`. There are transitive dependencies on `fs2`, `fs2-reactive-streams`, and `cats-effect`.

Next you'll need to add an implicit value:

```scala
implicit val sttpBackend = AsyncHttpClientFutureBackend()

// or, if you're using the scalaz version:
AsyncHttpClientScalazBackend().flatMap { implicit backend => ... }

// or, if you're using the zio version:
AsyncHttpClientZioBackend().flatMap { implicit backend => ... }

// or, if you're using the zio version with zio-streams for http streaming:
AsyncHttpClientZioStreamsBackend().flatMap { implicit backend => ... }

// or, if you're using the monix version:
AsyncHttpClientMonixBackend().flatMap { implicit backend => ... }

// or, if you're using the cats effect version:
AsyncHttpClientCatsBackend[cats.effect.IO]().flatMap { implicit backend => ... }

// or, if you're using the fs2 version:
AsyncHttpClientFs2Backend[cats.effect.IO]().flatMap { implicit backend => ... }

// or, if you'd like to use custom configuration:
implicit val sttpBackend = AsyncHttpClientFutureBackend.usingConfig(asyncHttpClientConfig)

// or, if you'd like to use adjust the configuration sttp creates:
implicit val sttpBackend = AsyncHttpClientFutureBackend.usingConfigBuilder(adjustFunction, sttpOptions)

// or, if you'd like to instantiate the AsyncHttpClient yourself:
implicit val sttpBackend = AsyncHttpClientFutureBackend.usingClient(asyncHttpClient)
```

## Streaming using Monix

The Monix backend supports streaming (as both Monix and Async Http Client support reactive streams `Publisher` s out of the box). The type of supported streams in this case is `Observable[ByteBuffer]`. That is, you can set such an observable as a request body:

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

## Streaming using fs2

The fs2 backend supports streaming in any instance of the `cats.effect.Effect` typeclass, such as `cats.effect.IO`. If `IO` is used then the type of supported streams is `fs2.Stream[IO, ByteBuffer]`.

Requests can be sent with a streaming body like this:

```scala
import sttp.client._
import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend

import java.nio.ByteBuffer
import cats.effect.{ContextShift, IO}
import fs2.Stream

implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)
val effect = AsyncHttpClientFs2Backend[IO]().flatMap { implicit backend =>
  val stream: Stream[IO, ByteBuffer] = ...

  basicRequest
    .streamBody(stream)
    .post(uri"...")
}
// run the effect
```

Responses can also be streamed:

```scala
import sttp.client._
import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend

import java.nio.ByteBuffer
import cats.effect.{ContextShift, IO}
import fs2.Stream
import scala.concurrent.duration.Duration

implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)
val effect = AsyncHttpClientFs2Backend[IO]().flatMap { implicit backend =>
  val response: IO[Response[Either[String, Stream[IO, ByteBuffer]]]] =
    basicRequest
      .post(uri"...")
      .response(asStream[Stream[IO, ByteBuffer]])
      .readTimeout(Duration.Inf)
      .send()

  response
}
// run the effect
```

## Streaming using zio-streams

The zio-streams backend supports streaming of type `Stream[Throwable, ByteBuffer]`.

Requests can be sent with a streaming body like this:

```scala
import sttp.client._
import sttp.client.asynchttpclient.zio._

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
import sttp.client.asynchttpclient.zio._

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

The async-http-client backend supports websockets, where the websocket handler is of type `sttp.client.asynchttpclient.WebSocketHandler`. An instance of this handler can be created in two ways.

The first ("high-level" one), available when using the Monix, ZIO and fs2 backends, is to pass a `MonixWebSocketHandler()`, `ZIOWebSocketHandler()` or `Fs2WebSocketHandler()`. This will create a websocket handler and expose a `sttp.client.ws.WebSocket[Task]` (for Monix and ZIO) / `sttp.client.ws.WebSocket[F]` (for fs2 and any `F[_] : ConcurrentEffect`) interface for sending/receiving messages.

See [websockets](../requests/websockets.html) for details on how to use the high-level interface.

Second ("low-level"), given an async-http-client-native `org.asynchttpclient.ws.WebSocketListener`, you can lift it to a web socket handler using `WebSocketHandler.fromListener`. This listener will receive lifecycle callbacks, as well as a callback each time a message is received. Note that the callbacks will be executed on the Netty (network) thread, so make sure not to run any blocking operations there, and delegate to other executors/thread pools if necessary. The value returned in the `WebSocketResponse` will be an instance of `org.asynchttpclient.ws.WebSocket`, which allows sending messages.

## Streaming websockets using fs2

For fs2, there are additionally some high-level helpers collected in `sttp.client.asynchttpclient.fs2.Fs2Websockets` which provide means to run the whole websocket communication through an `fs2.Pipe`. Example for a simple echo client:

```scala
import cats.effect.IO
import cats.implicits._
import sttp.client._
import sttp.client.ws._
import sttp.model.ws.WebSocketFrame

basicRequest
  .get(uri"wss://echo.websocket.org")
  .openWebsocketF(Fs2WebSocketHandler())
  .flatMap { response =>
    Fs2WebSockets.handleSocketThroughTextPipe(response.result) { in =>
      val receive = in.evalMap(m => IO(println("Received")))
      val send = Stream("Message 1".asRight, "Message 2".asRight, WebSocketFrame.close.asLeft)
      send merge receive.drain
    }
  }
```
