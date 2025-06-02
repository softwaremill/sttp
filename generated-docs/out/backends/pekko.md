# Pekko backend

This backend is based on [pekko-http](https://pekko.apache.org/docs/pekko-http/current/). To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client4" %% "pekko-http-backend" % "4.0.8"
```

A fully **asynchronous** backend. Uses the `Future` effect to return responses. There are also [other `Future`-based backends](future.md), which don't depend on Pekko. 

Note that you'll also need an explicit dependency on pekko-streams, as pekko-http doesn't depend on any specific pekko-streams version. So you'll also need to add, for example:

```
"org.apache.pekko" %% "pekko-stream" % "1.1.3"
```

Next you'll need to add create the backend instance:

```scala
import sttp.client4.pekkohttp.*
val backend = PekkoHttpBackend()
```

or, if you'd like to use an existing actor system:

```scala
import sttp.client4.pekkohttp.*
import org.apache.pekko.actor.ActorSystem

val actorSystem: ActorSystem = ???
val backend = PekkoHttpBackend.usingActorSystem(actorSystem)
```

This backend supports sending and receiving [pekko-streams](https://pekko.apache.org/docs/pekko/current/stream/index.html) streams. The streams capability is represented as `sttp.client4.pekkohttp.PekkoStreams`.

To set the request body as a stream:

```scala
import sttp.capabilities.pekko.PekkoStreams
import sttp.client4.*

import org.apache.pekko
import pekko.stream.scaladsl.Source
import pekko.util.ByteString

val source: Source[ByteString, Any] = ???

basicRequest
  .post(uri"...")
  .streamBody(PekkoStreams)(source)
```

To receive the response body as a stream:

```scala
import scala.concurrent.Future
import sttp.capabilities.pekko.PekkoStreams
import sttp.client4.*
import sttp.client4.pekkohttp.PekkoHttpBackend

import org.apache.pekko
import pekko.stream.scaladsl.Source
import pekko.util.ByteString

val backend = PekkoHttpBackend()

val response: Future[Response[Either[String, Source[ByteString, Any]]]] =
  basicRequest
    .post(uri"...")
    .response(asStreamUnsafe(PekkoStreams))
    .send(backend)
```

The pekko-http backend support both regular and streaming [websockets](../other/websockets.md).

## Testing

Apart from testing using [the stub](../testing/stub.md), you can create a backend using any `HttpRequest => Future[HttpResponse]` function, or an pekko-http `Route`.

That way, you can "mock" a server that the backend will talk to, without starting any actual server or making any HTTP calls.

If your application provides a client library for its dependants to use, this is a great way to ensure that the client actually matches the routes exposed by your application:

```scala
import sttp.client4.pekkohttp.*
import org.apache.pekko
import pekko.http.scaladsl.server.Route
import pekko.actor.ActorSystem

val route: Route = ???
implicit val system: ActorSystem = ???

val backend = PekkoHttpBackend.usingClient(system, http = PekkoHttpClient.stubFromRoute(route))
```

## WebSockets

Non-standard behavior:

* pekko always automatically responds with a `Pong` to a `Ping` message
* `WebSocketFrame.Ping` and `WebSocketFrame.Pong` frames are ignored; instead, you can configure automatic [keep-alive pings](https://pekko.apache.org/docs/pekko-http/current/client-side/websocket-support.html#automatic-keep-alive-ping-support)

## Server-sent events

Received data streams can be parsed to a stream of server-sent events (SSE):

```scala
import scala.concurrent.Future

import org.apache.pekko.stream.scaladsl.Source

import sttp.capabilities.pekko.PekkoStreams
import sttp.client4.pekkohttp.PekkoHttpServerSentEvents
import sttp.model.sse.ServerSentEvent
import sttp.client4._

def processEvents(source: Source[ServerSentEvent, Any]): Future[Unit] = ???

basicRequest
  .get(uri"...")
  .response(asStream(PekkoStreams)(stream => 
    processEvents(stream.via(PekkoHttpServerSentEvents.parse))))
```
