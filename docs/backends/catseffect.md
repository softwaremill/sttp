# cats-effect backend

The [Cats Effect](https://github.com/typelevel/cats-effect) backend is **asynchronous**. It can be created for any type implementing the `cats.effect.Concurrent` typeclass, such as `cats.effect.IO`. Sending a request is a non-blocking, lazily-evaluated operation and results in a wrapped response. There's a transitive dependency on `cats-effect`. 

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client" %% "async-http-client-backend-cats" % "2.0.0-RC13"
```
           
This backend depends on [async-http-client](https://github.com/AsyncHttpClient/async-http-client), uses [Netty](http://netty.io) behind the scenes and supports effect cancellation. 

Alternatively, the [http4s](http4s.html) backend can also be created for a type implementing the cats-effect's `Effect` typeclass, and supports streaming as in [fs2](fs2.html).  

Next you'll need to add an implicit value:

```scala
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend

AsyncHttpClientCatsBackend().flatMap { implicit backend => ... }

// or, if you'd like to use custom configuration:
AsyncHttpClientCatsBackend.usingConfig(asyncHttpClientConfig).flatMap { implicit backend => ... }

// or, if you'd like to use adjust the configuration sttp creates:
AsyncHttpClientCatsBackend.usingConfigBuilder(adjustFunction, sttpOptions).flatMap { implicit backend => ... }

// or, if you'd like the backend to be wrapped in cats-effect Resource:
AsyncHttpClientCatsBackend.resource().use { implicit backend => ... }

// or, if you'd like to instantiate the AsyncHttpClient yourself:
implicit val sttpBackend = AsyncHttpClientCatsBackend.usingClient(asyncHttpClient)
```

## Streaming

This backend doesn't support non-blocking [streaming](../requests/streaming.html).

## Websockets

The backend supports websockets by wrapping a [low-level Java interface](../websockets.html), `sttp.client.asynchttpclient.WebSocketHandler`.