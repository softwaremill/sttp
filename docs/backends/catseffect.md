# cats-effect backend

The [Cats Effect](https://github.com/typelevel/cats-effect) backend is **asynchronous**. It can be created for any type implementing the `cats.effect.Concurrent` typeclass, such as `cats.effect.IO`. Sending a request is a non-blocking, lazily-evaluated operation and results in a wrapped response. There's a transitive dependency on `cats-effect`. 

## Using async-http-client

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client3" %% "async-http-client-backend-cats" % "@VERSION@"
```

You'll need the following imports and implicits to create the backend:

```scala mdoc:silent
import sttp.client3._
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend
import cats.effect._

// an implicit `cats.effect.ContextShift` in required to create the backend; here, for `cats.effect.IO`:
implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
```
           
This backend depends on [async-http-client](https://github.com/AsyncHttpClient/async-http-client), uses [Netty](http://netty.io) behind the scenes. 

Alternatively, the [http4s](http4s.md) backend can also be created for a type implementing the cats-effect's `Effect` typeclass, and supports streaming as in [fs2](fs2.md).

Next you'll need to define a backend instance. This can be done in two basic ways:

* by creating an effect, which describes how the backend is created, or instantiating the backend directly. In this case, you'll need to close the backend manually
* by creating a `Resource`, which will instantiate the backend and close it after it has been used

A non-comprehensive summary of how the backend can be created is as follows:

```scala mdoc:compile-only
// the type class instance needs to be provided explicitly (e.g. `cats.effect.IO`). 
// the effect type must implement the Concurrent typeclass
AsyncHttpClientCatsBackend[IO]().flatMap { backend => ??? }
```

or, if you'd like to use a custom configuration:

```scala mdoc:compile-only
import org.asynchttpclient.AsyncHttpClientConfig
val config: AsyncHttpClientConfig = ???
AsyncHttpClientCatsBackend.usingConfig[IO](config).flatMap { backend => ??? }
```

or, if you'd like to use adjust the configuration sttp creates:

```scala mdoc:compile-only
import org.asynchttpclient.DefaultAsyncHttpClientConfig

val sttpOptions: SttpBackendOptions = SttpBackendOptions.Default 
val adjustFunction: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder = ???
AsyncHttpClientCatsBackend.usingConfigBuilder[IO](adjustFunction, sttpOptions).flatMap { backend => ??? }
```

or, if you'd like the backend to be wrapped in cats-effect Resource:

```scala mdoc:compile-only
AsyncHttpClientCatsBackend.resource[IO]().use { backend => ??? }
```

or, if you'd like to instantiate the AsyncHttpClient yourself:

```scala mdoc:compile-only
import org.asynchttpclient.AsyncHttpClient

val asyncHttpClient: AsyncHttpClient = ??? 
val backend = AsyncHttpClientCatsBackend.usingClient[IO](asyncHttpClient)
```

## Using Armeria backend

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "armeria-backend-cats" % "@VERSION@"
```

add imports:

```scala
import sttp.client3.armeria.cats.ArmeriaCatsBackend
import cats.effect.{ContextShift, IO}
```

create client:

```scala
implicit val cs: ContextShift[IO] = IO.contextShift(scala.concurrent.ExecutionContext.global)
val backend = ArmeriaCatsBackend[IO]()
```

or, if you'd like to instantiate the `WebClient` yourself:

```scala
import com.linecorp.armeria.client.WebClient

val client: WebClient = ???
val backend = ArmeriaCatsBackend.usingClient(client)
```

## Streaming

This backend doesn't support non-blocking [streaming](../requests/streaming.md).

## Websockets

The backend doesn't support [websockets](../websockets.md).
