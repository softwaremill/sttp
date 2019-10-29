async-http-client backend
=========================

To use, add the following dependency to your project::

  "com.softwaremill.sttp.client" %% "async-http-client-backend-future" % "2.0.0-M8"
  // or
  "com.softwaremill.sttp.client" %% "async-http-client-backend-scalaz" % "2.0.0-M8"
  // or
  "com.softwaremill.sttp.client" %% "async-http-client-backend-zio" % "2.0.0-M8"
  // or
  "com.softwaremill.sttp.client" %% "async-http-client-backend-zio-streams" % "2.0.0-M8"
  // or
  "com.softwaremill.sttp.client" %% "async-http-client-backend-monix" % "2.0.0-M8"
  // or
  "com.softwaremill.sttp.client" %% "async-http-client-backend-cats" % "2.0.0-M8"
  // or
  "com.softwaremill.sttp.client" %% "async-http-client-backend-fs2" % "2.0.0-M8"

This backend depends on `async-http-client <https://github.com/AsyncHttpClient/async-http-client>`_.
A fully **asynchronous** backend, which uses `Netty <http://netty.io>`_ behind the
scenes.

The responses are wrapped depending on the dependency chosen in either a:

* standard Scala ``Future``
* `Scalaz <https://github.com/scalaz/scalaz>`_ ``Task``. There's a transitive dependency on ``scalaz-concurrent``.
* `ZIO <https://github.com/zio/zio>`_ ``IO``. There's a transitive dependency on ``zio``.
* `Monix <https://monix.io>`_ ``Task``. There's a transitive dependency on ``monix-eval``.
* Any type implementing the `Cats Effect <https://github.com/typelevel/cats-effect>`_ ``Async`` typeclass, such as ``cats.effect.IO``. There's a transitive dependency on ``cats-effect``.
* `fs2 <https://github.com/functional-streams-for-scala/fs2>`_ ``Stream``. There are transitive dependencies on ``fs2``, ``fs2-reactive-streams``, and ``cats-effect``.

Next you'll need to add an implicit value::

  implicit val sttpBackend = AsyncHttpClientFutureBackend()

  // or, if you're using the scalaz version:
  implicit val sttpBackend = AsyncHttpClientScalazBackend()

  // or, if you're using the zio version:
  implicit val sttpBackend = AsyncHttpClientZioBackend()

  // or, if you're using the zio version with zio-streams for http streaming:
  implicit val sttpBackend = AsyncHttpClientZioStreamsBackend()

  // or, if you're using the monix version:
  implicit val sttpBackend = AsyncHttpClientMonixBackend()

  // or, if you're using the cats effect version:
  implicit val sttpBackend = AsyncHttpClientCatsBackend[cats.effect.IO]()

  // or, if you're using the fs2 version:
  implicit val sttpBackend = AsyncHttpClientFs2Backend[cats.effect.IO]()

  // or, if you'd like to use custom configuration:
  implicit val sttpBackend = AsyncHttpClientFutureBackend.usingConfig(asyncHttpClientConfig)

  // or, if you'd like to use adjust the configuration sttp creates:
  implicit val sttpBackend = AsyncHttpClientFutureBackend.usingConfigBuilder(adjustFunction, sttpOptions)

  // or, if you'd like to instantiate the AsyncHttpClient yourself:
  implicit val sttpBackend = AsyncHttpClientFutureBackend.usingClient(asyncHttpClient)

Streaming using Monix
---------------------

The Monix backend supports streaming (as both Monix and Async Http Client support reactive streams ``Publisher`` s out of the box). The type of supported streams in this case is ``Observable[ByteBuffer]``. That is, you can set such an observable as a request body::

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

And receive responses as an observable stream::

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

Streaming using fs2
-------------------

The fs2 backend supports streaming in any instance of the ``cats.effect.Effect`` typeclass, such as ``cats.effect.IO``. If ``IO`` is used then the type of supported streams is ``fs2.Stream[IO, ByteBuffer]``.

Requests can be sent with a streaming body like this::

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

Responses can also be streamed::

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

Websockets
----------

The async-http-client backend supports websockets, where the websocket handler is of type ``sttp.client.asynchttpclient.WebSocketHandler``. An instance of this handler can be created in two ways.

First, given an async-http-client-native ``org.asynchttpclient.ws.WebSocketListener``, you can lift it to a web socket handler using ``WebSocketHandler.fromListener``. This listener will receive lifecycle callbacks, as well as a callback each time a message is received. Note that the callbacks will be executed on the Netty (network) thread, so make sure not to run any blocking operations there, and delegate to other executors/thread pools if necessary. The value returned in the ``WebSocketResponse`` will be an instance of ``org.asynchttpclient.ws.WebSocket``, which allows sending messages.

The second approach, available when using the Monix, ZIO and fs2 backends, is to pass a ``MonixWebSocketHandler()``, ``ZIOWebSocketHandler()`` or ``Fs2WebSocketHandler()``. This will create a listener, which will internally buffer incoming messages, and expose a ``sttp.client.ws.WebSocket[Task]`` (``sttp.client.ws.WebSocket[F]`` for fs2 and any ``F[_] : ConcurrentEffect``) interface for sending/receiving messages.

Specifically, the ``WebSocket[Task]`` interface contains two methods, both of which return a ``Task`` (a lazily-evaluated description of a side-effecting, asynchronous process):

* ``def receive: Task[Either[WebSocketEvent.Close, WebSocketFrame.Incoming]]`` which will complete once a message is available, and return either information that the websocket has been closed, or the incoming message
* ``def send(f: WebSocketFrame, isContinuation: Boolean = false): Task[Unit]``, which should be used to send a message to the websocket. The ``WebSocketFrame`` companion object contains methods for creating binary/text messages. When using fragmentation, the first message should be sent using ``finalFragment = false``, and subsequent messages using ``isContinuation = true``.

There are also other methods for receiving only text/binary messages, as well as automatically sending ``Pong`` responses when a ``Ping`` is received.

Example usage::

  import monix.eval.Task
  import sttp.client._
  import sttp.client.ws.{WebSocket, WebSocketResponse}
  import sttp.model.ws.WebSocketFrame

  val response: Task[WebSocketResponse[WebSocket[Task]]] = basicRequest
    .get(uri"wss://echo.websocket.org")
    .openWebsocket(MonixWebSocketHandler())

  response.flatMap { r =>
    val ws: WebSocket[Task] = r.result
    val send = ws.send(WebSocketFrame.text("Hello!")
    val receive = ws.receiveText().flatMap(t => Task(println(s"RECEIVED: $t")))
    val close = ws.close()
    send.flatMap(_ => receive).flatMap(_ => close)
  }
