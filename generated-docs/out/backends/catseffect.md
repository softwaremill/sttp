# cats-effect backend

The [Cats Effect](https://github.com/typelevel/cats-effect) backend is **asynchronous**. 
It can be created for any type implementing the `cats.effect.Concurrent` typeclass, such as `cats.effect.IO`. 
Sending a request is a non-blocking, lazily-evaluated operation and results in a wrapped response. 
There's a transitive dependency on `cats-effect`. 

Note that all [fs2](fs2.md) backends also support any cats-effect effect, additionally supporting request & response streaming.

Also note that the [http4s](http4s.md) backend can also be created for a type implementing the cats-effect’s `Async` typeclass, and supports streaming as in [fs2](fs2.md).

## Using HttpClient

Firstly, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client4" %% "cats" % "4.0.0-M13"
```

Obtain a cats-effect `Resource` which creates the backend, and closes the thread pool after the resource is no longer used:

```scala
import cats.effect.IO
import sttp.client4.httpclient.cats.HttpClientCatsBackend

HttpClientCatsBackend.resource[IO]().use { backend => ??? }
```

or, by providing a custom `Dispatcher`:

```scala
import cats.effect.IO
import cats.effect.std.Dispatcher
import sttp.client4.httpclient.cats.HttpClientCatsBackend

val dispatcher: Dispatcher[IO] = ???

HttpClientCatsBackend[IO](dispatcher).flatMap { backend => ??? }
```

or, if you'd like to instantiate the `HttpClient` yourself:

```scala
import cats.effect.IO
import cats.effect.std.Dispatcher
import java.net.http.HttpClient
import sttp.client4.httpclient.cats.HttpClientCatsBackend

val httpClient: HttpClient = ???
val dispatcher: Dispatcher[IO] = ???

val backend = HttpClientCatsBackend.usingClient[IO](httpClient, dispatcher)
```

or, obtain a cats-effect `Resource` with a custom instance of the `HttpClient`:

```scala
import cats.effect.IO
import java.net.http.HttpClient
import sttp.client4.httpclient.cats.HttpClientCatsBackend

val httpClient: HttpClient = ???

HttpClientCatsBackend.resourceUsingClient[IO](httpClient).use { backend => ??? }
```

This backend is based on the built-in `java.net.http.HttpClient` available from Java 11 onwards.

Host header override is supported in environments running Java 12 onwards, but it has to be enabled by system property:

```
-Djdk.httpclient.allowRestrictedHeaders=host
```

## Using Armeria

Creation of the backend can be done in two basic ways:

* by creating an effect, which describes how the backend is created, or instantiating the backend directly. In this case, you'll need to close the backend manually
* by creating a `Resource`, which will instantiate the backend and close it after it has been used.

Firstly, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client4" %% "armeria-backend-cats" % "4.0.0-M13" // for cats-effect 3.x
// or
"com.softwaremill.sttp.client4" %% "armeria-backend-cats-ce2" % "4.0.0-M13" // for cats-effect 2.x
```

create client:

```scala
import cats.effect.IO
import sttp.client4.armeria.cats.ArmeriaCatsBackend

val backend = ArmeriaCatsBackend[IO]()

// You can use the default client which reuses the connection pool of ClientFactory.ofDefault()
ArmeriaCatsBackend.usingDefaultClient[IO]()
```

or, if you'd like the backend to be wrapped in cats-effect `Resource`:

```scala
import cats.effect.IO
import sttp.client4.armeria.cats.ArmeriaCatsBackend

ArmeriaCatsBackend.resource[IO]().use { backend => ??? }
```

or, if you'd like to instantiate the [WebClient](https://armeria.dev/docs/client-http) yourself:

```scala
import cats.effect.IO
import com.linecorp.armeria.client.WebClient
import com.linecorp.armeria.client.circuitbreaker._
import sttp.client4.armeria.cats.ArmeriaCatsBackend

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
