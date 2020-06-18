# Websockets

Apart from [streaming](requests/streaming.md), backends (see [backends summary](backends/summary.md)) can also optionally support websockets. Websocket requests are described exactly the same as regular requests, starting with `basicRequest`, adding headers, specifying the request method and uri.

The difference is that `openWebsocket(handler)` should be called instead of `send()`, given an instance of a backend-specific websocket handler. Refer to documentation of individual backends for details on how to instantiate the handler.

As with regular requests, instead of calling `request.openWebsocket(handler)` and using an implicit backend instance, it is also possible to call `backend.openWebsocket(request, handler)`.

If creating the websocket handler is a side-effecting operation (and the handler is wrapped with an effects wrapper), the `openWebsocketF(handler)` can be used.

After opening a websocket, a `sttp.client.ws.WebSocketResponse` instance is returned, wrapped in a backend-specific effects wrapper, such as `Future`, `IO`, `Task` or no wrapper for synchronous backends. If the protocol upgrade hasn't been successful, the request will fail with an error (represented as an exception or a failed effects wrapper).

In case of success, `WebSocketResponse` contains:

* the headers returned when opening the websocket
* a handler-specific and backend-specific value, which can be used to interact with the websocket, or somehow representing the result of the connection

## Websocket handlers

Each backend which supports websockets, does so through a backend-specific websocket handler. Depending on the backend, this can be an implementation of a "low-level" Java listener interface, a "high-level" interface build on top of these listeners, or a backend-specific Scala stream. 

The type of the handler is determined by the third type parameter of `SttpBackend`.

## Streaming websockets

The following backends support streaming websockets:

* [Akka](backends/akka.md#websockets)
* [fs2](backends/fs2.md#websockets)

## Using the high-level websocket interface

The high-level, "functional" interface to websockets is available when using the following backends and handlers:
 
* [Monix](backends/monix.md) and `MonixWebSocketHandler` from the appropriate package
* [ZIO](backends/zio.md) and `sttp.client.asynchttpclient.zio.ZioWebSocketHandler`
* [fs2](backends/fs2.md) and `sttp.client.asynchttpclient.fs2.Fs2WebSocketHandler`.

```eval_rst
.. note::
  The listeners created by the high-level handlers internally buffer incoming websocket events. In some implementations, when creating the handler, a bound can be specified for the size of the buffer. If the bound is specified and the buffer fills up (as can happen if the messages are not received, or processed slowly), the websocket will error and close. Otherwise, the buffer will potentially take up all available memory.
```

When the websocket is open, the `WebSocketResponse` will contain an instance of `sttp.client.ws.WebSocket[F]`, where `F` is the backend-specific effects wrapper, such as `IO` or `Task`. This interface contains two methods, both of which return computations wrapped in the effects wrapper `F` (which typically is lazily-evaluated description of a side-effecting, asynchronous process):

* `def receive: F[Either[WebSocketEvent.Close, WebSocketFrame.Incoming]]` which will complete once a message is available, and return either information that the websocket has been closed, or the incoming message
* `def send(f: WebSocketFrame, isContinuation: Boolean = false): F[Unit]`, which should be used to send a message to the websocket. The `WebSocketFrame` companion object contains methods for creating binary/text messages. When using fragmentation, the first message should be sent using `finalFragment = false`, and subsequent messages using `isContinuation = true`.

There are also other methods for receiving only text/binary messages, as well as automatically sending `Pong` responses when a `Ping` is received.

If there's an error, a failed effects wrapper will be returned, containing one of the `sttp.client.ws.WebSocketException` exceptions, or a backend-specific exception.

Example usage with the [Monix](backends/monix.md) variant of the async-http-client backend:

```scala
import monix.eval.Task
import sttp.client._
import sttp.client.ws.{WebSocket, WebSocketResponse}
import sttp.model.ws.WebSocketFrame
import sttp.client.asynchttpclient.monix.MonixWebSocketHandler
import sttp.client.asynchttpclient.WebSocketHandler

implicit val backend: SttpBackend[Task, Observable[ByteBuffer], WebSocketHandler] = ...

val response: Task[WebSocketResponse[WebSocket[Task]]] = basicRequest
  .get(uri"wss://echo.websocket.org")
  .openWebsocketF(MonixWebSocketHandler())

response.flatMap { r =>
  val ws: WebSocket[Task] = r.result
  val send = ws.send(WebSocketFrame.text("Hello!"))
  val receive = ws.receiveText().flatMap(t => Task(println(s"RECEIVED: $t")))
  send.flatMap(_ => receive).flatMap(_ => ws.close)
}
```

## Using the low-level websocket interface

Given a backend-native low-level Java interface, you can lift it to a web socket handler using `WebSocketHandler.fromListener` (from the appropriate package). This listener will receive lifecycle callbacks, as well as a callback each time a message is received. Note that the callbacks will be executed on the network thread, so make sure not to run any blocking operations there, and delegate to other executors/thread pools if necessary. The value returned in the `WebSocketResponse` will be a backend-native instance.
 
The types of the handlers, low-level Java interfaces and resulting websocket interfaces are, depending on the backend implementation:

* `sttp.client.asynchttpclient.WebSocketHandler` / `org.asynchttpclient.ws.WebSocketListener` / `org.asynchttpclient.ws.WebSocket` 
* `sttp.client.okhttp.WebSocketHandler` / `okhttp3.WebSocketListener` / `okhttp3.WebSocket`
* `sttp.client.httpclient.WebSocketHandler` / `java.net.http.WebSocket.Listener` / `java.net.http.WebSocket`
