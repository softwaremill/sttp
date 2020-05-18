# cats-effect backend

The [Cats Effect](https://github.com/typelevel/cats-effect) backend is **asynchronous**. It can be created for any type implementing the `cats.effect.Concurrent` typeclass, such as `cats.effect.IO`. Sending a request is a non-blocking, lazily-evaluated operation and results in a wrapped response. There's a transitive dependency on `cats-effect`. 

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client" %% "async-http-client-backend-cats" % "2.1.2"
```
           
This backend depends on [async-http-client](https://github.com/AsyncHttpClient/async-http-client), uses [Netty](http://netty.io) behind the scenes and supports effect cancellation. 

Alternatively, the [http4s](http4s.html) backend can also be created for a type implementing the cats-effect's `Effect` typeclass, and supports streaming as in [fs2](fs2.html).  

Next you'll need to define a backend instance as an implicit value. This can be done in two basic ways:

* by creating an effect, which describes how the backend is created, or instantiating the backend directly. In this case, you'll need to close the backend manually
* by creating a `Resource`, which will instantiate the backend and close it after it has been used

A non-comprehensive summary of how the backend can be created is as follows:

```scala
import sttp.client.asynchttpclient.cats.AsyncHttpClientCatsBackend
import cats.effect._

// an implicit ContextShift in required to create the backend; here, for `cats.effect.IO`:
implicit val cs: ContextShift[IO] = IO.contextShift( scala.concurrent.ExecutionContext.global )

// the type class instance needs to be provided explicitly (e.g. `cats.effect.IO`). 
// the effect type must implement the Concurrent typeclass
AsyncHttpClientCatsBackend[IO]().flatMap { implicit backend => ... }

// or, if you'd like to use custom configuration:
AsyncHttpClientCatsBackend.usingConfig[IO](asyncHttpClientConfig).flatMap { implicit backend => ... }

// or, if you'd like to use adjust the configuration sttp creates:
AsyncHttpClientCatsBackend.usingConfigBuilder[IO](adjustFunction, sttpOptions).flatMap { implicit backend => ... }

// or, if you'd like the backend to be wrapped in cats-effect Resource:
AsyncHttpClientCatsBackend.resource[IO]().use { implicit backend => ... }

// or, if you'd like to instantiate the AsyncHttpClient yourself:
implicit val sttpBackend = AsyncHttpClientCatsBackend.usingClient[IO](asyncHttpClient)
```

## Streaming

This backend doesn't support non-blocking [streaming](../requests/streaming.html).

## Websockets

The backend supports websockets by wrapping a [low-level Java interface](../websockets.html), `sttp.client.asynchttpclient.WebSocketHandler`.