# Akka backend

This backend is based on [akka-http](http://doc.akka.io/docs/akka-http/current/scala/http/). To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "akka-http-backend" % "3.3.10"
```

A fully **asynchronous** backend. Uses the `Future` effect to return responses. There are also [other `Future`-based backends](future.md), which don't depend on Akka. 

Note that you'll also need an explicit dependency on akka-streams, as akka-http doesn't depend on any specific akka-streams version. So you'll also need to add, for example:

```
"com.typesafe.akka" %% "akka-stream" % "2.6.15"
```

Next you'll need to add create the backend instance:

```scala
import sttp.client3.akkahttp._
val backend = AkkaHttpBackend()
```

or, if you'd like to use an existing actor system:

```scala
import sttp.client3.akkahttp._
import akka.actor.ActorSystem

val actorSystem: ActorSystem = ???
val backend = AkkaHttpBackend.usingActorSystem(actorSystem)
```

This backend supports sending and receiving [akka-streams](http://doc.akka.io/docs/akka/current/scala/stream/index.html) streams. The streams capability is represented as `sttp.client3.akkahttp.AkkaStreams`.

To set the request body as a stream:

```scala
import sttp.capabilities.akka.AkkaStreams
import sttp.client3._

import akka.stream.scaladsl.Source
import akka.util.ByteString

val source: Source[ByteString, Any] = ???

basicRequest
  .streamBody(AkkaStreams)(source)
  .post(uri"...")
```

To receive the response body as a stream:

```scala
import scala.concurrent.Future
import sttp.capabilities.akka.AkkaStreams
import sttp.client3._
import sttp.client3.akkahttp.AkkaHttpBackend

import akka.stream.scaladsl.Source
import akka.util.ByteString

val backend = AkkaHttpBackend()

val response: Future[Response[Either[String, Source[ByteString, Any]]]] =
  basicRequest
    .post(uri"...")
    .response(asStreamUnsafe(AkkaStreams))
    .send(backend)
```

The akka-http backend support both regular and streaming [websockets](../websockets.md).

## Testing

Apart from testing using [the stub](../testing.md), you can create a backend using any `HttpRequest => Future[HttpResponse]` function, or an akka-http `Route`.

That way, you can "mock" a server that the backend will talk to, without starting any actual server or making any HTTP calls.

If your application provides a client library for its dependants to use, this is a great way to ensure that the client actually matches the routes exposed by your application:

```scala
import sttp.client3.akkahttp._
import akka.http.scaladsl.server.Route
import akka.actor.ActorSystem

val route: Route = ???
implicit val system: ActorSystem = ???

val backend = AkkaHttpBackend.usingClient(system, http = AkkaHttpClient.stubFromRoute(route))
```

## WebSockets

Non-standard behavior:

* akka always automatically responds with a `Pong` to a `Ping` message
* `WebSocketFrame.Ping` and `WebSocketFrame.Pong` frames are ignored; instead, you can configure automatic [keep-alive pings](https://doc.akka.io/docs/akka-http/current/client-side/websocket-support.html#automatic-keep-alive-ping-support)

## Server-sent events

Received data streams can be parsed to a stream of server-sent events (SSE):

```scala
import scala.concurrent.Future

import akka.stream.scaladsl.Source

import sttp.capabilities.akka.AkkaStreams
import sttp.client3.akkahttp.AkkaHttpServerSentEvents
import sttp.model.sse.ServerSentEvent
import sttp.client3._

def processEvents(source: Source[ServerSentEvent, Any]): Future[Unit] = ???

basicRequest.response(asStream(AkkaStreams)(stream => 
  processEvents(stream.via(AkkaHttpServerSentEvents.parse))))
```
