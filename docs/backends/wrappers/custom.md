# Custom backends

It is also entirely possible to write custom backends (if doing so, please consider contributing!) or wrap an existing one. One can even write completely generic wrappers for any delegate backend, as each backend comes equipped with a monad for the used effect type. This brings the possibility to `map` and `flatMap` over responses.

Possible use-cases for wrapper-backend include:

* logging
* capturing metrics
* request signing (transforming the request before sending it to the delegate)

See also the section on [resilience](../../resilience.md) which covers topics such as retries, circuit breaking and rate limiting.

## Request tagging

Each request contains a `tags: Map[String, Any]` map. This map can be used to tag the request with any backend-specific information, and isn't used in any way by sttp itself.

Tags can be added to a request using the `def tag(k: String, v: Any)` method, and read using the `def tag(k: String): Option[Any]` method.

Backends, or backend wrappers can use tags e.g. for logging, passing a metric name, using different connection pools, or even different delegate backends.

## Listener backend

The `sttp.client4.listener.ListenerBackend` can make it easier to create backend wrappers which need to be notified about request lifecycle events: when a request is started, and when it completes either successfully or with an exception. This is possible by implementing a `sttp.client4.listener.RequestListener`. This is how e.g. the [slf4j backend](logging.md) is implemented. 

A request listener can associate a value with a request, which will then be passed to the request completion notification methods.

A side-effecting request listener, of type `RequestListener[Identity, L]`, can be lifted to a request listener `RequestListener[F, L]` given a `MonadError[F]`, using the `RequestListener.lift` method.

## Backend wrappers and redirects

See the appropriate section in docs on [redirects](../../conf/redirects.md).

## Logging backend wrapper

A good example on how to implement a logging backend wrapper is the [logging](logging.md) backend wrapper implementation. It uses the `ListenerBackend` to get notified about request lifecycle events.

To adjust the logs to your needs, or to integrate with your logging framework, simply copy the code and modify as needed. 

## Example metrics backend wrapper

Below is an example on how to implement a backend wrapper, which sends
metrics for completed requests and wraps any `Future`-based backend:

```scala mdoc:compile-only
import sttp.capabilities.Effect
import sttp.client4._
import sttp.client4.akkahttp._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util._
// the metrics infrastructure
trait MetricsServer {
  def reportDuration(name: String, duration: Long): Unit
}

class CloudMetricsServer extends MetricsServer {
  override def reportDuration(name: String, duration: Long): Unit = ???
}

// the backend wrapper
abstract class MetricWrapper[P](delegate: GenericBackend[Future, P],
                       metrics: MetricsServer)
    extends DelegateBackend(delegate) {

  override def send[T](request: GenericRequest[T, P with Effect[Future]]): Future[Response[T]] = {
    val start = System.currentTimeMillis()

    def report(metricSuffix: String): Unit = {
      val metricPrefix = request.tag("metric").getOrElse("?")
      val end = System.currentTimeMillis()
      metrics.reportDuration(metricPrefix + "-" + metricSuffix, end - start)
    }

    delegate.send(request).andThen {
      case Success(response) if response.is200 => report("ok")
      case Success(response)                   => report("notok")
      case Failure(t)                          => report("exception")
    }
  }
}

object MetricWrapper {
  def apply[S](
    backend: WebSocketStreamBackend[Future, S],
    metrics: MetricsServer
  ): WebSocketStreamBackend[Future, S] =
    new MetricWrapper(backend, metrics) with WebSocketStreamBackend[Future, S] {}
}

// example usage
val backend = MetricWrapper(AkkaHttpBackend(), new CloudMetricsServer())

basicRequest
  .get(uri"http://company.com/api/service1")
  .tag("metric", "service1")
  .send(backend)
```

See also the [Prometheus](prometheus.md) backend for an example implementation.

## Example retrying backend wrapper

Handling retries is a complex problem when it comes to HTTP requests. When is a request retryable? There are a couple of things to take into account:

* connection exceptions are generally good candidates for retries
* only idempotent HTTP methods (such as `GET`) could potentially be retried
* some HTTP status codes might also be retryable (e.g. `500 Internal Server Error` or `503 Service Unavailable`)

In some cases it's possible to implement a generic retry mechanism; such a mechanism should take into account logging, metrics, limiting the number of retries and a backoff mechanism. These mechanisms could be quite simple, or involve e.g. retry budgets (see [Finagle's](https://twitter.github.io/finagle/guide/Clients.md#retries) documentation on retries). In sttp, it's possible to recover from errors using the `responseMonad`. A starting point for a retrying backend could be:

```scala mdoc:compile-only
import sttp.capabilities.Effect
import sttp.client4._

class RetryingBackend[F[_], P](
    delegate: GenericBackend[F, P],
    shouldRetry: RetryWhen,
    maxRetries: Int)
    extends DelegateBackend(delegate) {

  override def send[T](request: GenericRequest[T, P with Effect[F]]): F[Response[T]] = {
    sendWithRetryCounter(request, 0)
  }

  private def sendWithRetryCounter[T](
    request: GenericRequest[T, P with Effect[F]], retries: Int): F[Response[T]] = {

    val r = responseMonad.handleError(delegate.send(request)) {
      case t if shouldRetry(request, Left(t)) && retries < maxRetries =>
        sendWithRetryCounter(request, retries + 1)
    }

    responseMonad.flatMap(r) { resp =>
      if (shouldRetry(request, Right(resp)) && retries < maxRetries) {
        sendWithRetryCounter(request, retries + 1)
      } else {
        responseMonad.unit(resp)
      }
    }
  }
}

object RetryingBackend {
  def apply[F[_]](backend: WebSocketBackend[F], shouldRetry: RetryWhen, maxRetries: Int): WebSocketBackend[F] =
    new RetryingBackend(backend, shouldRetry, maxRetries) with WebSocketBackend[F] {}
}
```                    

## Example backend with circuit breaker

> "When a system is seriously struggling, failing fast is better than making clients wait."

There are many libraries that can help you achieve such a behavior: [hystrix](https://github.com/Netflix/Hystrix), [resilience4j](https://github.com/resilience4j/resilience4j), [akka's circuit breaker](https://doc.akka.io/docs/akka/current/common/circuitbreaker.html) or [monix catnap](https://monix.io/docs/3x/catnap/circuit-breaker.html) to name a few. Despite some small differences, both their apis and functionality are very similar, that's why we didn't want to support each of them explicitly.

Below is an example on how to implement a backend wrapper, which integrates with circuit-breaker module from resilience4j library and wraps any backend:

```scala mdoc:compile-only
import io.github.resilience4j.circuitbreaker.{CallNotPermittedException, CircuitBreaker}
import sttp.capabilities.Effect
import sttp.client4.{GenericBackend, GenericRequest, Backend, Response, DelegateBackend}
import sttp.monad.MonadError
import java.util.concurrent.TimeUnit

class CircuitSttpBackend[F[_], P](
    circuitBreaker: CircuitBreaker,
    delegate: GenericBackend[F, P]) extends DelegateBackend(delegate) {

  override def send[T](request: GenericRequest[T, P with Effect[F]]): F[Response[T]] = {
    CircuitSttpBackend.decorateF(circuitBreaker, delegate.send(request))
  }
}

object CircuitSttpBackend {

  def apply[F[_]](circuitBreaker: CircuitBreaker, backend: Backend[F]): Backend[F] =
    new CircuitSttpBackend(circuitBreaker, backend) with Backend[F] {}

  def decorateF[F[_], T](
      circuitBreaker: CircuitBreaker,
      service: => F[T]
  )(implicit monadError: MonadError[F]): F[T] = {
    monadError.suspend {
      if (!circuitBreaker.tryAcquirePermission()) {
        monadError.error(CallNotPermittedException
                              .createCallNotPermittedException(circuitBreaker))
      } else {
        val start = System.nanoTime()
        try {
          monadError.handleError(monadError.map(service) { r =>
            circuitBreaker.onSuccess(System.nanoTime() - start, TimeUnit.NANOSECONDS)
            r
          }) {
            case t =>
              circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, t)
              monadError.error(t)
          }
        } catch {
          case t: Throwable =>
            circuitBreaker.onError(System.nanoTime() - start, TimeUnit.NANOSECONDS, t)
            monadError.error(t)
        }
      }
    }
  }
}
```      

## Example backend with rate limiter

> "Prepare for a scale and establish reliability and HA of your service."

Below is an example on how to implement a backend wrapper, which integrates with rate-limiter module from resilience4j library and wraps any backend:

```scala mdoc:compile-only
import io.github.resilience4j.ratelimiter.RateLimiter
import sttp.capabilities.Effect
import sttp.monad.MonadError
import sttp.client4.{GenericBackend, GenericRequest, Response, StreamBackend, DelegateBackend}

class RateLimitingSttpBackend[F[_], P](
    rateLimiter: RateLimiter,
    delegate: GenericBackend[F, P]
    )(implicit monadError: MonadError[F]) extends DelegateBackend(delegate) {

  override def send[T](request: GenericRequest[T, P with Effect[F]]): F[Response[T]] = {
    RateLimitingSttpBackend.decorateF(rateLimiter, delegate.send(request))
  }
}

object RateLimitingSttpBackend {
  def apply[F[_], S](
    rateLimiter: RateLimiter,
    backend: StreamBackend[F, S]
  )(implicit monadError: MonadError[F]): StreamBackend[F, S] =
    new RateLimitingSttpBackend(rateLimiter, backend) with StreamBackend[F, S] {}

  def decorateF[F[_], T](
      rateLimiter: RateLimiter,
      service: => F[T]
  )(implicit monadError: MonadError[F]): F[T] = {
    monadError.suspend { 
      try {
        RateLimiter.waitForPermission(rateLimiter)
        service
      } catch {
        case t: Throwable =>
          monadError.error(t)
      }
    }
  }
}
```         

## Example new backend

Implementing a new backend is made easy as the tests are published in the `core` jar file under the `tests` classifier. Simply add the follow dependencies to your `build.sbt`:

```
"com.softwaremill.sttp.client4" %% "core" % "@VERSION@" % Test classifier "tests"
```

Implement your backend and extend the `HttpTest` class:

```scala mdoc:compile-only
import sttp.client4._
import sttp.client4.testing.{ConvertToFuture, HttpTest}
import scala.concurrent.Future

class MyCustomBackendHttpTest extends HttpTest[Future] {
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
  override lazy val backend: Backend[Future] = ??? //new MyCustomBackend()
  override def timeoutToNone[T](t: Future[T], timeoutMillis: Int): Future[Option[T]] = ???
}
```

You can find a more detailed example in the [sttp-vertx](https://github.com/guymers/sttp-vertx) repository.

## Custom backend wrapper using cats

When implementing a backend wrapper using cats, it might be useful to import:

```scala
import sttp.client4.impl.cats.implicits._
```

from the cats integration module. The module should be available on the classpath after adding following dependency:

```scala
"com.softwaremill.sttp.client4" %% "cats" % "@VERSION@" // for cats-effect 3.x
// or
"com.softwaremill.sttp.client4" %% "catsce2" % "@VERSION@" // for cats-effect 2.x
```

The object contains implicits to convert a cats `MonadError` into the sttp `MonadError`, 
as well as a way to map the effects wrapper used with the `.mapK` extension method for the backend. 
