# Custom backends

It is also entirely possible to write custom backends (if doing so, please consider contributing!) or wrap an existing one. One can even write completely generic wrappers for any delegate backend, as each backend comes equipped with a monad for the used effect type. This brings the possibility to `map` and `flatMap` over responses.

Possible use-cases for wrapper-backend include:

* logging
* capturing metrics
* request signing (transforming the request before sending it to the delegate)

See also the section on [resilience](../../other/resilience.md) which covers topics such as retries, circuit breaking and rate limiting.

## Request attributes

Each request contains a `attributes: AttributeMap` type-safe map. This map can be used to tag the request with any backend-specific information, and isn't used in any way by sttp itself.

Attributes can be added to a request using the `def attribute[T](k: AttributeKey[T], v: T)` method, and read using the `def attribute[T](k: Attribute[T]): Option[T]` method.

Backends, or backend wrappers can use attributes e.g. for logging, passing a metric name, using different connection pools, or even different delegate backends.

## Listener backend

The `sttp.client4.listener.ListenerBackend` can make it easier to create backend wrappers which need to be notified about request lifecycle events: when a request is started, and when it completes either successfully or with an exception. This is possible by implementing a `sttp.client4.listener.RequestListener`. This is how e.g. the [slf4j backend](logging.md) is implemented. 

A request listener can associate a value with a request, which will then be passed to the request completion notification methods.

A side-effecting request listener, of type `RequestListener[Identity, L]`, can be lifted to a request listener `RequestListener[F, L]` given a `MonadError[F]`, using the `RequestListener.lift` method.

## Backend wrappers and redirects

See the appropriate section in docs on [redirects](../../conf/redirects.md).

## Logging backend wrapper

A good example on how to implement a logging backend wrapper is the [logging](logging.md) backend wrapper implementation. It uses the `ListenerBackend` to get notified about request lifecycle events.

To adjust the logs to your needs, or to integrate with your logging framework, simply copy the code and modify as needed. 
  
## Examples backend wrappers

A number of example backend wrappers can be found in [examples](../../examples.md).

## Example new backend

Implementing a new backend is made easy as the tests are published in the `core` jar file under the `tests` classifier. Simply add the follow dependencies to your `build.sbt`:

```
"com.softwaremill.sttp.client4" %% "core" % "4.0.0-M25" % Test classifier "tests"
```

Implement your backend and extend the `HttpTest` class:

```scala
import sttp.client4.*
import sttp.client4.testing.{ConvertToFuture, HttpTest}
import scala.concurrent.Future

class MyCustomBackendHttpTest extends HttpTest[Future]:
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
  override val backend: Backend[Future] = ??? //new MyCustomBackend()
  override def timeoutToNone[T](t: Future[T], timeoutMillis: Int): Future[Option[T]] = ???
```

## Custom backend wrapper using cats

When implementing a backend wrapper using cats, it might be useful to import:

```scala
import sttp.client4.impl.cats.implicits.*
```

from the cats integration module. The module should be available on the classpath after adding following dependency:

```scala
"com.softwaremill.sttp.client4" %% "cats" % "4.0.0-M25" // for cats-effect 3.x
// or
"com.softwaremill.sttp.client4" %% "catsce2" % "4.0.0-M25" // for cats-effect 2.x
```

The object contains implicits to convert a cats `MonadError` into the sttp `MonadError`, 
as well as a way to map the effects wrapper used with the `.mapK` extension method for the backend. 
