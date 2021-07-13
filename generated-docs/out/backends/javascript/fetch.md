# JavaScript (Fetch) backend

A JavaScript backend with web socket support. Implemented using the [Fetch API](https://developer.mozilla.org/en-US/docs/Web/API/Fetch_API).

## `Future`-based 

This is the default backend, available in the main jar for JS. To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %%% "core" % "3.3.10"
```

And create the backend instance:

```scala
val backend = FetchBackend()
```
Timeouts are handled via the new [AbortController](https://developer.mozilla.org/en-US/docs/Web/API/AbortController) class. As this class only recently appeared in browsers you may need to add a [polyfill](https://www.npmjs.com/package/abortcontroller-polyfill).

As browsers do not allow access to redirect responses, if a request sets `followRedirects` to false then a redirect will cause the response to return an error.

Note that `Fetch` does not pass cookies by default. If your request needs cookies then you will need to pass a `FetchOptions` instance with `credentials` set to either `RequestCredentials.same-origin` or `RequestCredentials.include` depending on your requirements.

## Monix-based

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %%% "monix" % "3.3.10"
```

And create the backend instance:

```scala
val backend = FetchMonixBackend()
```

## cats-effect-based

Any effect implementing the cats-effect `Concurrent` typeclass can be used. To use, add the following dependency to 
your project:

```
"com.softwaremill.sttp.client3" %%% "cats" % "3.3.10"
```

And create the backend instance:

```scala
val backend = FetchCatsBackend[IO]()
```

## Node.js

Using `FetchBackend` is possible with [node-fetch](https://www.npmjs.com/package/node-fetch) module
and [ws with isomorphic-ws](https://www.npmjs.com/package/ws) module for web sockets.

```
npm install --save node-fetch isomorphic-ws ws
```

It has to be loaded into your runtime. This can be done in your main method as such:

```scala
val g = scalajs.js.Dynamic.global.globalThis

val nodeFetch = g.require("node-fetch")

g.fetch = nodeFetch
g.Headers = nodeFetch.Headers
g.Request = nodeFetch.Request
g.WebSocket = g.require("isomorphic-ws")
```

## Streaming

Streaming support is provided via `FetchMonixBackend`. Note that streaming support on Firefox is hidden behind a flag, see
[ReadableStream](https://developer.mozilla.org/en-US/docs/Web/API/ReadableStream) for more information.

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %%% "monix" % "3.3.10"
```

An example of streaming a response:

```scala   
import sttp.client3._
import sttp.client3.impl.monix._

import java.nio.ByteBuffer
import monix.eval.Task
import monix.reactive.Observable

val backend = FetchMonixBackend()

val response: Task[Response[Observable[ByteBuffer]]] =
  sttp
    .post(uri"...")
    .response(asStreamUnsafe(MonixStreams))
    .send(backend)
```      

```eval_rst
.. note:: Currently no browsers support passing a stream as the request body. As such, using the ``Fetch`` backend with a streaming request will result in it being converted into an in-memory array before being sent. Response bodies are returned as a "proper" stream.
```

## Websockets

The backend supports both regular and streaming [websockets](../websockets.md).

## Server-sent events

Received data streams can be parsed to a stream of server-sent events (SSE), when using the Monix variant:

```scala
import monix.reactive.Observable
import monix.eval.Task

import sttp.capabilities.monix.MonixStreams
import sttp.client3.impl.monix.MonixServerSentEvents
import sttp.model.sse.ServerSentEvent
import sttp.client3._

def processEvents(source: Observable[ServerSentEvent]): Task[Unit] = ???

basicRequest.response(asStream(MonixStreams)(stream => 
  processEvents(stream.transform(MonixServerSentEvents.parse))))
```