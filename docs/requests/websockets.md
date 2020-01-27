# Websockets

Apart from streaming, backends (see [backends summary](../backends/summary.html)) can also optionally support websockets. Websocket requests are described exactly the same as regular requests, starting with `basicRequest`, adding headers, specifying the request method and uri.

The difference is that `openWebsocket(handler)` should be called instead of `send()`, given an instance of a backend-specific websocket handler. Refer to documentation of individual backends for details on how to instantiate the handler.

If creating the websocket handler is a side-effecting operation (and the handler is wrapped with an effects wrapper), the `openWebsocketF(handler)` can be used.

After opening a websocket, a `WebSocketResponse` instance is returned, wrapped in a backend-specific effects wrapper, such as `Future`, `IO`, `Task` or no wrapper for synchronous backends. If the protocol upgrade hasn't been successful, the request will fail with an error (represented as an exception or a failed effects wrapper).

In case of success, `WebSocketResponse` contains:

* the headers returned when opening the websocket
* a handler-specific and backend-specific value, which can be used to interact with the websocket, or somehow representing the result of the connection

## Low-level and high-level websocket handlers

Each backend which supports websockets, does so through a backend-specific websocket handler. Depending on the backend, this can be an implementation of a "low-level" Java listener interface (as in [async-http-client](../backends/asynchttpclient.html), [OkHttp](../backends/okhttp.html) and [HttpClient](../backends/httpclient.html)), a Scala stream (as in [akka-http](../backends/akkahttp.html) and [fs2](../backends/asynchttpclient.html)), or some other
other approach.

Additionally, some backends, on top of the "low-level" Java listeners also offer a higher-level, more "functional" approach to websockets. This is done by passing a specific handler instance when opening the websocket; refer to the documentation of individual backends for details.

```eval_rst
.. note::
  The listeners created by the high-level handlers internally buffer incoming websocket events. In some implementations, when creating the handler, a bound can be specified for the size of the buffer. If the bound is specified and the buffer fills up (as can happen if the messages are not received, or processed slowly), the websocket will error and close. Otherwise, the buffer will potentially take up all available memory.
```

## Using the high-level websocket interface

The high-level interface is available when using Monix, ZIO or fs2 [async-http-client](../backends/asynchttpclient.html) backends, the Monix [OkHttp](../backends/okhttp.html) backend, or the Monix [HttpClient](../backends/httpclient.html) backend.

When the websocket is open, the response will contain an instance of `sttp.client.ws.WebSocket[F]`, where `F` is the backend-specific effects wrapper, such as `IO` or `Task`. This interface contains two methods, both of which return computations wrapped in the effects wrapper `F` (which typically is lazily-evaluated description of a side-effecting, asynchronous process):

* `def receive: F[Either[WebSocketEvent.Close, WebSocketFrame.Incoming]]` which will complete once a message is available, and return either information that the websocket has been closed, or the incoming message
* `def send(f: WebSocketFrame, isContinuation: Boolean = false): F[Unit]`, which should be used to send a message to the websocket. The `WebSocketFrame` companion object contains methods for creating binary/text messages. When using fragmentation, the first message should be sent using `finalFragment = false`, and subsequent messages using `isContinuation = true`.

There are also other methods for receiving only text/binary messages, as well as automatically sending `Pong` responses when a `Ping` is received.

If there's an error, a failed effects wrapper will be returned, containing one of the `sttp.client.ws.WebSocketError` exceptions, or a backend-specific exception.

Example usage with the Monix variant of the [async-http-client backend](../backends/asynchttpclient.html):

```scala
import monix.eval.Task
import sttp.client._
import sttp.client.ws.{WebSocket, WebSocketResponse}
import sttp.model.ws.WebSocketFrame

val response: Task[WebSocketResponse[WebSocket[Task]]] = basicRequest
  .get(uri"wss://echo.websocket.org")
  .openWebsocketF(MonixWebSocketHandler())

response.flatMap { r =>
  val ws: WebSocket[Task] = r.result
  val send = ws.send(WebSocketFrame.text("Hello!")
  val receive = ws.receiveText().flatMap(t => Task(println(s"RECEIVED: $t")))
  val close = ws.close()
  send.flatMap(_ => receive).flatMap(_ => close)
}
```

The high-level handler can be further wrapped to obtain a stream, see the section on fs2 websockets in
[async-http-client](../backends/asynchttpclient.html).
