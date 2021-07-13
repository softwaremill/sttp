# cats-effect backend

The [Cats Effect](https://github.com/typelevel/cats-effect) backend is **asynchronous**. It can be created for any type implementing the `cats.effect.Concurrent` typeclass, such as `cats.effect.IO`. Sending a request is a non-blocking, lazily-evaluated operation and results in a wrapped response. There's a transitive dependency on `cats-effect`. 

## Using async-http-client

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client3" %% "async-http-client-backend-cats" % "3.3.10" // for cats-effect 3.x
// or
"com.softwaremill.sttp.client3" %% "async-http-client-backend-cats-ce2" % "3.3.10" // for cats-effect 2.x
```
           
This backend depends on [async-http-client](https://github.com/AsyncHttpClient/async-http-client), uses [Netty](http://netty.io) behind the scenes. 

Alternatively, the [http4s](http4s.md) backend can also be created for a type implementing the cats-effect's `Async` typeclass, and supports streaming as in [fs2](fs2.md).

Next you'll need to define a backend instance. This can be done in two basic ways:

* by creating an effect, which describes how the backend is created, or instantiating the backend directly. In this case, you'll need to close the backend manually
* by creating a `Resource`, which will instantiate the backend and close it after it has been used

A non-comprehensive summary of how the backend can be created is as follows:

```scala
import cats.effect.IO
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend

// the type class instance needs to be provided explicitly (e.g. `cats.effect.IO`). 
// the effect type must implement the Async typeclass
AsyncHttpClientCatsBackend[IO]().flatMap { backend => ??? }
```

or, if you'd like to use a custom configuration:

```scala
import cats.effect.IO
import org.asynchttpclient.AsyncHttpClientConfig
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend

val config: AsyncHttpClientConfig = ???
AsyncHttpClientCatsBackend.usingConfig[IO](config).flatMap { backend => ??? }
```

or, if you'd like to use adjust the configuration sttp creates:

```scala
import cats.effect.IO
import org.asynchttpclient.DefaultAsyncHttpClientConfig
import sttp.client3.SttpBackendOptions
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend

val sttpOptions: SttpBackendOptions = SttpBackendOptions.Default  
val adjustFunction: DefaultAsyncHttpClientConfig.Builder => DefaultAsyncHttpClientConfig.Builder = ???
AsyncHttpClientCatsBackend.usingConfigBuilder[IO](adjustFunction, sttpOptions).flatMap { backend => ??? }
```

or, if you'd like the backend to be wrapped in cats-effect `Resource`:

```scala
import cats.effect.IO
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend

AsyncHttpClientCatsBackend.resource[IO]().use { backend => ??? }
```

or, if you'd like to instantiate the `AsyncHttpClient` yourself:

```scala
import cats.effect.IO
import org.asynchttpclient.AsyncHttpClient
import sttp.client3.asynchttpclient.cats.AsyncHttpClientCatsBackend

val asyncHttpClient: AsyncHttpClient = ???  
val backend = AsyncHttpClientCatsBackend.usingClient[IO](asyncHttpClient)
```

## Using Armeria

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client3" %% "armeria-backend-cats" % "3.3.10" // for cats-effect 3.x
// or
"com.softwaremill.sttp.client3" %% "armeria-backend-cats-ce2" % "3.3.10" // for cats-effect 2.x
```

create client:

```scala
import cats.effect.IO
import sttp.client3.armeria.cats.ArmeriaCatsBackend

val backend = ArmeriaCatsBackend[IO]()

// You can use the default client which reuses the connection pool of ClientFactory.ofDefault()
ArmeriaCatsBackend.usingDefaultClient[IO]()
```

or, if you'd like to instantiate the [WebClient](https://armeria.dev/docs/client-http) yourself:

```scala
import cats.effect.IO
import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.client.circuitbreaker._
import sttp.client3.armeria.cats.ArmeriaCatsBackend

// Fluently build Armeria WebClient with built-in decorators
val client = WebClient.builder("https://my-service.com")
             // Open circuit on 5xx server error status
             .decorator(CircuitBreakerClient.newDecorator(CircuitBreaker.ofDefaultName(),
               CircuitBreakerRule.onServerErrorStatus()))
             .build()

val backend = ArmeriaCatsBackend.usingClient[IO](client)
```

```eval_rst
.. note:: A WebClient could fail to follow redirects if the WebClient is created with a base URI and a redirect location is a different URI.
```

This backend is build on top of [Armeria](https://armeria.dev/docs/client-http).
Armeria's [ClientFactory](https://armeria.dev/docs/client-factory) manages connections and protocol-specific properties.
Please visit [the official documentation](https://armeria.dev/docs/client-factory) to learn how to configure it.

## Streaming

This backend doesn't support non-blocking [streaming](../requests/streaming.md).

## Websockets

The backend doesn't support [websockets](../websockets.md).
