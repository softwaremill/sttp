# Usage examples

All of the examples are available [in the sources](https://github.com/softwaremill/sttp/blob/master/examples/src/main/scala/sttp/client/examples) in runnable form.

## POST a form using the synchronous backend

Required dependencies:

```scala            
libraryDependencies ++= List("com.softwaremill.sttp.client" %% "core" % "2.2.2")
```

Example code:

```scala
import sttp.client._

val signup = Some("yes")

val request = basicRequest
  // send the body as form data (x-www-form-urlencoded)
  .body(Map("name" -> "John", "surname" -> "doe"))
  // use an optional parameter in the URI
  .post(uri"https://httpbin.org/post?signup=$signup")

implicit val backend = HttpURLConnectionBackend()
val response = request.send()

println(response.body)
println(response.headers)
```

## GET and parse JSON using the akka-http backend and json4s

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client" %% "akka-http-backend" % "2.2.2",
  "com.softwaremill.sttp.client" %% "json4s" % "2.2.2",
  "org.json4s" %% "json4s-native" % "3.6.0"
)
```

Example code:

```scala
import scala.concurrent.Future
import sttp.client._
import sttp.client.akkahttp._
import sttp.client.json4s._

import scala.concurrent.ExecutionContext.Implicits.global

case class HttpBinResponse(origin: String, headers: Map[String, String])

implicit val serialization = org.json4s.native.Serialization
implicit val formats = org.json4s.DefaultFormats
val request = basicRequest
  .get(uri"https://httpbin.org/get")
  .response(asJson[HttpBinResponse])

implicit val backend = AkkaHttpBackend()
val response: Future[Response[Either[ResponseError[Exception], HttpBinResponse]]] =
  request.send()

for {
  r <- response
} {
  println(s"Got response code: ${r.code}")
  println(r.body)
  backend.close()
}
```

## GET and parse JSON using the ZIO async-http-client backend and circe

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % "2.2.2",
  "com.softwaremill.sttp.client" %% "circe" % "2.2.2",
  "io.circe" %% "circe-generic" % "0.12.1"
)
```

Example code:

```scala
import sttp.client._
import sttp.client.circe._
import sttp.client.asynchttpclient.zio._
import io.circe.generic.auto._
import zio._
import zio.console.Console

object GetAndParseJsonZioCirce extends zio.App {

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {

    case class HttpBinResponse(origin: String, headers: Map[String, String])

    val request = basicRequest
      .get(uri"https://httpbin.org/get")
      .response(asJson[HttpBinResponse])

    // create a description of a program, which requires two dependencies in the environment:
    // the SttpClient, and the Console
    val sendAndPrint: ZIO[Console with SttpClient, Throwable, Unit] = for {
      response <- SttpClient.send(request)
      _ <- console.putStrLn(s"Got response code: ${response.code}")
      _ <- console.putStrLn(response.body.toString)
    } yield ()

    // provide an implementation for the SttpClient dependency; other dependencies are
    // provided by Zio
    sendAndPrint.provideCustomLayer(AsyncHttpClientZioBackend.layer()).fold(_ => ExitCode.failure, _ => ExitCode.success)
  }
}
```

## POST and serialize JSON using the Monix async-http-client backend and circe

Required dependencies:

```scala
libraryDependencies ++= List(
  "com.softwaremill.sttp.client" %% "async-http-client-backend-monix" % "2.2.2",
  "com.softwaremill.sttp.client" %% "circe" % "2.2.2",
  "io.circe" %% "circe-generic" % "0.12.1"
)
```

Example code:

```scala
import sttp.client._
import sttp.client.circe._
import sttp.client.asynchttpclient.monix._
import io.circe.generic.auto._
import monix.eval.Task

case class Info(x: Int, y: String)

val postTask = AsyncHttpClientMonixBackend().flatMap { implicit backend =>
  val r = basicRequest
    .body(Info(91, "abc"))
    .post(uri"https://httpbin.org/post")

  r.send()
    .flatMap { response =>
      Task(println(s"""Got ${response.code} response, body:\n${response.body}"""))
    }
    .guarantee(backend.close())
}

import monix.execution.Scheduler.Implicits.global
postTask.runSyncUnsafe()
```

## Test an endpoint which requires multiple query parameters

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client" %% "core" % "2.2.2")
```

Example code:

```scala
import sttp.client._
import sttp.client.testing._

implicit val backend = SttpBackendStub.synchronous
  .whenRequestMatches(_.uri.paramsMap.contains("filter"))
  .thenRespond("Filtered")
  .whenRequestMatches(_.uri.path.contains("secret"))
  .thenRespond("42")

val parameters1 = Map("filter" -> "name=mary", "sort" -> "asc")
println(
  basicRequest
    .get(uri"http://example.org?search=true&$parameters1")
    .send()
    .body)

val parameters2 = Map("sort" -> "desc")
println(
  basicRequest
    .get(uri"http://example.org/secret/read?$parameters2")
    .send()
    .body)
```

## Open a websocket using the high-level websocket interface and ZIO

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % "2.2.2")
```

Example code:

```scala
import sttp.client._
import sttp.client.asynchttpclient.zio._
import sttp.client.ws.WebSocket
import sttp.model.ws.WebSocketFrame
import zio.{App => ZApp, _}
import zio.console.Console

object WebsocketZio extends ZApp {
  def useWebsocket(ws: WebSocket[Task]): ZIO[Console, Throwable, Unit] = {
    def send(i: Int) = ws.send(WebSocketFrame.text(s"Hello $i!"))
    val receive = ws.receiveText().flatMap(t => console.putStrLn(s"RECEIVED: $t"))
    send(1) *> send(2) *> receive *> receive *> ws.close
  }

  // create a description of a program, which requires two dependencies in the environment:
  // the SttpClient, and the Console
  val sendAndPrint: ZIO[Console with SttpClient, Throwable, Unit] = for {
    response <- SttpClient.openWebsocket(basicRequest.get(uri"wss://echo.websocket.org"))
    _ <- useWebsocket(response.result)
  } yield ()

  override def run(args: List[String]): ZIO[ZEnv, Nothing, ExitCode] = {
    // provide an implementation for the SttpClient dependency; other dependencies are
    // provided by Zio
    sendAndPrint.provideCustomLayer(AsyncHttpClientZioBackend.layer()).fold(_ => ExitCode.failure, _ => ExitCode.success)
  }
}
```

## Open a websocket using the high-level websocket interface and Monix

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client" %% "async-http-client-backend-monix" % "2.2.2")
```

Example code:

```scala
import monix.eval.Task
import sttp.client._
import sttp.client.ws.WebSocket
import sttp.model.ws.WebSocketFrame
import sttp.client.asynchttpclient.monix.{AsyncHttpClientMonixBackend, MonixWebSocketHandler}

object WebsocketMonix extends App {
  import monix.execution.Scheduler.Implicits.global

  def useWebsocket(ws: WebSocket[Task]): Task[Unit] = {
    def send(i: Int) = ws.send(WebSocketFrame.text(s"Hello $i!"))
    val receive = ws.receiveText().flatMap(t => Task(println(s"RECEIVED: $t")))
    send(1) *> send(2) *> receive *> receive *> ws.close
  }

  val websocketTask: Task[Unit] = AsyncHttpClientMonixBackend().flatMap { implicit backend =>
    val response: Task[WebSocketResponse[WebSocket[Task]]] = basicRequest
      .get(uri"wss://echo.websocket.org")
      .openWebsocketF(MonixWebSocketHandler())

    response
      .flatMap(r => useWebsocket(r.result))
      .guarantee(backend.close())
  }

  websocketTask.runSyncUnsafe()
}
```

## Stream request and response bodies using fs2

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client" %% "async-http-client-backend-fs2" % "2.2.2")
```

Example code:

```scala
import sttp.client._
import sttp.client.asynchttpclient.fs2.AsyncHttpClientFs2Backend

import cats.effect.{ContextShift, IO}
import cats.instances.string._
import fs2.{Stream, text}

implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)

def streamRequestBody(implicit backend: SttpBackend[IO, Stream[IO, Byte], NothingT]): IO[Unit] = {
  val stream: Stream[IO, Byte] = Stream.emits("Hello, world".getBytes)

  basicRequest
    .streamBody(stream)
    .post(uri"https://httpbin.org/post")
    .send()
    .map { response => println(s"RECEIVED:\n${response.body}") }
}

def streamResponseBody(implicit backend: SttpBackend[IO, Stream[IO, Byte], NothingT]): IO[Unit] = {
  basicRequest
    .body("I want a stream!")
    .post(uri"https://httpbin.org/post")
    .response(asStreamUnsafeAlways[Stream[IO, Byte]])
    .send()
    .flatMap { response =>
      response.body
        .chunks
        .through(text.utf8DecodeC)
        .compile
        .foldMonoid
    }
    .map { body => println(s"RECEIVED:\n$body") }
}

val effect = AsyncHttpClientFs2Backend[IO]().flatMap { implicit backend =>
  streamRequestBody.flatMap(_ => streamResponseBody).guarantee(backend.close())
}

effect.unsafeRunSync()
``` 

## Retry a request using ZIO

Required dependencies:

```scala
libraryDependencies ++= List("com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % "2.2.2")
```

Example code:

```scala
import sttp.client._
import sttp.client.asynchttpclient.zio.AsyncHttpClientZioBackend

import zio.{ZIO, Schedule}
import zio.clock.Clock
import zio.duration._

AsyncHttpClientZioBackend()
  .flatMap { implicit backend =>
    val localhostRequest = basicRequest
      .get(uri"http://localhost/test")
      .response(asStringAlways)

    val sendWithRetries: ZIO[Clock, Throwable, Response[String]] = localhostRequest
      .send()
      .either
      .repeat(
        Schedule.spaced(1.second) *>
          Schedule.recurs(10) *>
          Schedule.doWhile(result => RetryWhen.Default(localhostRequest, result))
      )
      .absolve

    sendWithRetries.ensuring(backend.close().catchAll(_ => ZIO.unit))
  }
````
