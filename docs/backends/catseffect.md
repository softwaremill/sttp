# cats-effect backend

The [Cats Effect](https://github.com/typelevel/cats-effect) backend is **asynchronous**. 
It can be created for any type implementing the `cats.effect.Concurrent` typeclass, such as `cats.effect.IO`. 
Sending a request is a non-blocking, lazily-evaluated operation and results in a wrapped response. 
There's a transitive dependency on `cats-effect`. 

Note that all [fs2](fs2.md) backends also support any cats-effect effect, additionally supporting request & response streaming.

Also note that the [http4s](http4s.md) backend can also be created for a type implementing the cats-effect’s `Async` typeclass, and supports streaming as in [fs2](fs2.md).


## Using Armeria

To use, add the following dependency to your project:

```scala
"com.softwaremill.sttp.client3" %% "armeria-backend-cats" % "@VERSION@" // for cats-effect 3.x
// or
"com.softwaremill.sttp.client3" %% "armeria-backend-cats-ce2" % "@VERSION@" // for cats-effect 2.x
```

create client:

```scala mdoc:silent
import cats.effect.IO
import sttp.client3.armeria.cats.ArmeriaCatsBackend

val backend = ArmeriaCatsBackend[IO]()

// You can use the default client which reuses the connection pool of ClientFactory.ofDefault()
ArmeriaCatsBackend.usingDefaultClient[IO]()
```

or, if you'd like to instantiate the [WebClient](https://armeria.dev/docs/client-http) yourself:

```scala mdoc:compile-only
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
