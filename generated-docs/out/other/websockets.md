# WebSockets

One of the optional capabilities that a backend can support are websockets (see [backends summary](../backends/summary.md)). Websocket requests are described exactly the same as regular requests, starting with `basicRequest`, adding headers, specifying the request method and uri.

A websocket request will be sent instead of a regular one if the response specification includes handling the response as a websocket. Depending on the backend you are using, there are three variants of websocket response specifications: synchronous, asynchronous and streaming. To use them, add one of the following imports:

* `import sttp.client4.ws.sync.*` if you are using a synchronous backend (such as `DefaultSyncBackend`), without any effect wrappers
* `import sttp.client4.ws.async.*` if you are using an asynchronous backend (e.g. based on `Future`s or `IO`s)
* `import sttp.client4.ws.stream.*` if you want to handle web socket messages using a non-blocking stream (e.g. `fs2.Stream` or `akka.stream.scaladsl.Source`)

The above imports will bring into scope a number of `asWebSocket(...)` methods, giving a couple of variants of working with websockets. Alternatively, you can extend the `SttpWebSocketSyncApi`, `SttpWebSocketAsyncApi` or `SttpWebSocketStreamApi` traits, to group all used sttp client features within a single object.

Refer to the documentation of individual backends for additional notes, or restrictions, when using WebSockets.

## Using `WebSocket`

The first possibility of interacting with web sockets is using `sttp.client4.ws.SyncWebSocket` (sync variant), or `sttp.ws.WebSocket[F]` (async variant), where `F` is the backend-specific effects wrapper, such as `Future` or `IO`. These classes contain two basic methods:
 
* `def receive: WebSocketFrame` (optionally wrapped with `F[_]` in the async variant) which will complete once a message is available, and return the next incoming frame (which can be a data, ping, pong or close)
* `def send(f: WebSocketFrame, isContinuation: Boolean = false): Unit` (again optionally wrapped with `F[_]`), which sends a message to the websocket. The `WebSocketFrame` companion object contains methods for creating binary/text messages. When using fragmentation, the first message should be sent using `finalFragment = false`, and subsequent messages using `isContinuation = true`.
 
The `SyncWebSocket` / `WebSocket` classes also contain other methods for receiving only text/binary messages, as well as automatically sending `Pong` responses when a `Ping` is received.

The following response specifications which use `SyncWebSocket` are available in the `sttp.client4.ws.sync` object (the second type parameter of `WebSocketResponseAs` specifies the type returned as the response body):

```scala
import sttp.client4.*
import sttp.client4.ws.SyncWebSocket
import sttp.model.ResponseMetadata
import sttp.shared.Identity

// when using import sttp.client4.ws.sync.*

def asWebSocket[T](f: SyncWebSocket => T): 
  WebSocketResponseAs[Identity, Either[String, T]] = ???

def asWebSocketOrFail[T](f: SyncWebSocket => T): 
  WebSocketResponseAs[Identity, T] = ???

def asWebSocketWithMetadata[T](
   f: (SyncWebSocket, ResponseMetadata) => T
): WebSocketResponseAs[Identity, Either[String, T]] = ???

def asWebSocketUnsafe: 
  WebSocketResponseAs[Identity, Either[String, SyncWebSocket]] = ???

def asWebSocketAlwaysUnsafe: 
  WebSocketResponseAs[Identity, SyncWebSocket] = ???
```

The first variant, `asWebSocket`, passes an open `SyncWebSocket` to the user-provided function. This function should only return once interaction with the websocket is finished. The backend can then safely close the websocket. The value that's returned as the response body is either an error (represented as a `String`), in case the websocket upgrade didn't complete successfully, or the value returned by the websocket-interacting method. 

The second (`asWebSocketOrFail`) is similar, but any errors due to failed websocket protocol upgrades are represented as exceptions.

The remaining two variants return the open `SyncWebSocket` directly, as the response body. It is then the responsibility of the client code to close the websocket, once it's no longer needed.

Similar response specifications, but using an effect wrapper and `WebSocket[F]`, are available in the `sttp.client4.ws.async` objet. 

See also the [examples](../examples.md), which include examples involving websockets.

## Using non-blocking, asynchronous streams

Another possibility is to work with websockets by providing a streaming stage, which transforms incoming data frames into outgoing frames. This can be e.g. a [Pekko](../backends/pekko.md) `Flow` or a [fs2](../backends/fs2.md) `Pipe`.

The following response specifications are available: 

```scala
import sttp.client4.*
import sttp.capabilities.{Streams, WebSockets}
import sttp.ws.WebSocketFrame

// when using import sttp.client4.ws.stream._

def asWebSocketStream[S](s: Streams[S])(p: s.Pipe[WebSocketFrame.Data[_], WebSocketFrame]): 
  WebSocketStreamResponseAs[Either[String, Unit], S] = ???

def asWebSocketStreamOrFail[S](s: Streams[S])(p: s.Pipe[WebSocketFrame.Data[_], WebSocketFrame]): 
  WebSocketStreamResponseAs[Unit, S] = ???
```

Using streaming websockets requires the backend to support the given streaming capability (see also [streaming](../requests/streaming.md)). Streaming capabilities are described as implementations of `Streams[S]`, and are provided by backend implementations, e.g. `PekkoStreams` or `Fs2Streams[F]`.

When working with streams of websocket frames keep in mind that a text payload may be fragmented into multiple frames.
sttp provides two useful methods (`fromTextPipe`, `fromTextPipeF`) for each backend to aggregate these fragments back into complete messages.
These methods can be found in corresponding WebSockets classes for given effect type:

```{eval-rst}
================ ==========================================
effect type      class name
================ ==========================================
``monix.Task``   ``sttp.client4.impl.monix.MonixWebSockets``   
``ZIO``          ``sttp.client4.impl.zio.ZioWebSockets``
``fs2.Stream``   ``sttp.client4.impl.fs2.Fs2WebSockets``
================ ==========================================
```

## Using blocking, synchronous Ox streams

[Ox](https://ox.softwaremill.com) is a Scala 3 toolkit that allows you to handle concurrency and resiliency in direct-style, leveraging Java 21 virtual threads.
If you're using Ox with `sttp`, you can use the `DefaultSyncBackend` from `sttp-core` for HTTP communication. An additional `ox` module allows handling WebSockets 
as Ox `Source` and `Sink`:

```
// sbt dependency
"com.softwaremill.sttp.client4" %% "ox" % "4.0.0-RC2"
```

```scala 
import ox.*
import ox.channels.{Sink, Source}
import sttp.client4.*
import sttp.client4.impl.ox.ws.* // import to access asSourceAnkSink
import sttp.client4.ws.SyncWebSocket
import sttp.client4.ws.sync.*
import sttp.ws.WebSocketFrame

def useWebSocket(ws: SyncWebSocket): Unit =
    supervised {
      // (Source[WebSocketFrame], Sink[WebSocketFrame])
      val (wsSource, wsSink) = asSourceAndSink(ws) 
      // ...
    }

val backend = DefaultSyncBackend()
basicRequest
  .get(uri"wss://ws.postman-echo.com/raw")
  .response(asWebSocket(useWebSocket))
  .send(backend)
```

See the [full example here](https://github.com/softwaremill/sttp/blob/master/examples/src/main/scala/sttp/client4/examples/ws/wsOxExample.scala).

Make sure that the `Source` is continually read. This will guarantee that server-side `Close` signal is received and handled. 
If you don't want to process frames from the server, you can at least handle it with a `fork { source.drain() }`.
  
You don't need to manually call `ws.close()` when using this approach, this will be handled automatically underneath, 
according to following rules:
 - If the request `Sink` is closed due to an upstream error, a `Close` frame is sent, and the `Source` with incoming responses gets completed as `Done`.
 - If the request `Sink` completes as `Done`, a `Close` frame is sent, and the response `Sink` keeps receiving responses until the server closes communication.
 - If the response `Source` is closed by a `Close` frame from the server or due to an error, the request Sink is closed as `Done`, which will still send all outstanding buffered frames, and then finish.

Read more about Ox, structured concurrency, Sources and Sinks on the [project website](https://ox.softwaremill.com).

## Compression

For those who plan to use a lot of websocket traffic, you could consider websocket compression, however it's often not supported:

### OkHttp based backends

* supports compression (default: not enabled)

### akka-http backend

Compression is not yet available, to track Akka developments in this area, see [this issue](https://github.com/akka/akka-http/issues/659).
