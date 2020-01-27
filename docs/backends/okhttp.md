# OkHttp backend

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client" %% "okhttp-backend" % "2.0.0-RC6"
// or, for the monix version:
"com.softwaremill.sttp.client" %% "okhttp-backend-monix" % "2.0.0-RC6"
```

This backend depends on [OkHttp](http://square.github.io/okhttp/), and
offers:

* a **synchronous** backend: `OkHttpSyncBackend`
* an **asynchronous**, `Future`-based backend: `OkHttpFutureBackend`
* an **asynchronous**, Monix-`Task`-based backend: `OkHttpMonixBackend`

OkHttp fully supports HTTP/2.

## Websockets

The OkHttp backend supports websockets, where the websocket handler is of type `sttp.client.okhttp.WebSocketHandler`. An instance of this handler can be created in two ways.

The first ("high-level" one), available when using the Monix variant, is to pass a `MonixWebSocketHandler()`. This will create a websocket handler and expose a `sttp.client.ws.WebSocket[Task]` interface for sending/receiving messages.

See [websockets](../requests/websockets.html) for details on how to use the high-level interface.

Second ("low-level"), given an OkHttp-native `okhttp3.WebSocketListener`, you can lift it to a web socket handler using `WebSocketHandler.fromListener`. This listener will receive lifecycle callbacks, as well as a callback each time a message is received. Note that the callbacks will be executed on the Netty (network) thread, so make sure not to run any blocking operations there, and delegate to other executors/thread pools if necessary. The value returned in the `WebSocketResponse` will be an instance of `okhttp3.WebSocket`, which allows sending messages.
