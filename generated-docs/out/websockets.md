# WebSockets

One of the optional capabilities (represented as `WebSockets`) that a backend can support are websockets (see [backends summary](backends/summary.md)). Websocket requests are described exactly the same as regular requests, starting with `basicRequest`, adding headers, specifying the request method and uri.

A websocket request will be sent instead of a regular one if the response specification includes handling the response as a websocket. A number of `asWebSocket(...)` methods are available, giving a couple of variants of working with websockets.

## Using `WebSocket`

The first possibility is using `sttp.client.ws.WebSocket[F]`, where `F` is the backend-specific effects wrapper, such as `Future` or `IO`. It contains two basic methods, both of which use the `F` effect to return results:
 
* `def receive: F[WebSocketFrame.Incoming]` which will complete once a message is available, and return the next incoming frame (which can be a data, ping, pong or close)
* `def send(f: WebSocketFrame, isContinuation: Boolean = false): F[Unit]`, which sends a message to the websocket. The `WebSocketFrame` companion object contains methods for creating binary/text messages. When using fragmentation, the first message should be sent using `finalFragment = false`, and subsequent messages using `isContinuation = true`.
 
The `WebSocket` trait also contains other methods for receiving only text/binary messages, as well as automatically sending `Pong` responses when a `Ping` is received.

The following response specifications which use `WebSocket[F]` are available (the first type parameter of `ResponseAs` specifies the type returned as the response body, the second - the capabilities that the backend is required to support to send the request):

```mdoc:compile-only
import sttp.client._

def asWebSocket[F[_], T](f: WebSocket[F] => F[T]): 
  ResponseAs[Either[String, T], Effect[F] with WebSockets] = ???

def asWebSocketAlways[F[_], T](f: WebSocket[F] => F[T]): 
  ResponseAs[T, Effect[F] with WebSockets] = ???

def asWebSocketUnsafe[F[_]]: 
  ResponseAs[Either[String, WebSocket[F]], Effect[F] with WebSockets] = ???

def asWebSocketUnsafeAlways[F[_]]: 
  ResponseAs[WebSocket[F], Effect[F] with WebSockets] = ???
```

The first variant, `asWebSocket`, passes an open `WebSocket` to the user-provided function. This function should return an effect which completes, once interaction with the websocket is finished. The backend can then safely close the websocket. The value that's returned as the response body is either an error (represented as a `String`), in case the websocket upgrade didn't complete successfully, or the value returned by the websocket-interacting method. 

The second variant (`asWebSocketAlways`) is similar, but any errors due to failed websocket protocol upgrades are represented as failed effects (exceptions).

The remaining two variants return the open `WebSocket` directly, as the response body. It is then the responsibility of the client code to close the websocket, once it's no longer needed.

See also the [examples](examples.md), which include examples involving websockets.

## Using streams

Another possibility is to work with websockets by providing a streaming stage, which transforms incoming data frames into outgoing frames. This can be e.g. an [Akka](backends/akka.md) `Flow` or a [fs2](backends/fs2.md) `Pipe`.

The following response specifications are available: 

```mdoc:compile-only
def asWebSocketStream[S](s: Streams[S])(p: s.Pipe[WebSocketFrame.Data[_], WebSocketFrame]): 
  ResponseAs[Either[String, Unit], S with WebSockets] = ???

def asWebSocketStreamAlways[S](s: Streams[S])(p: s.Pipe[WebSocketFrame.Data[_], WebSocketFrame]): 
  ResponseAs[Unit, S with WebSockets] = ???
```

Using streaming websockets requires the backend to support the given streaming capability (see also [streaming](requests/streaming.md)). Streaming capabilities are described as implementations of `Streams[S]`, and are provided by backend implementations, e.g. `AkkaStreams` or `Fs2Streams[F]`.
