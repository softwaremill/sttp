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

The `sttp.client3.listener.ListenerBackend` can make it easier to create backend wrappers which need to be notified about request lifecycle events: when a request is started, and when it completes either successfully or with an exception. This is possible by implementing a `sttp.client3.listener.RequestListener`. This is how e.g. the [slf4j backend](logging.md) is implemented. 

A request listener can associate a value with a request, which will then be passed to the request completion notification methods.

A side-effecting request listener, of type `RequestListener[Identity, L]`, can be lifted to a request listener `RequestListener[F, L]` given a `MonadError[F]`, using the `RequestListener.lift` method.

## Backend wrappers and redirects

By default redirects are handled at a low level, using a wrapper around the main, concrete backend: each of the backend factory methods, e.g. `HttpURLConnectionBackend()` returns a backend wrapped in `FollowRedirectsBackend`.

This causes any further backend wrappers to handle a request which involves redirects as one whole, without the intermediate requests. However, wrappers which collects metrics, implements tracing or handles request retries might want to handle every request in the redirect chain. This can be achieved by layering another `FollowRedirectsBackend` on top of the wrapper. Only the top-level follow redirects backend will handle redirects, other follow redirect wrappers (at lower levels) will be disabled.

For example:

```scala
import sttp.capabilities.Effect
import sttp.client3._
import sttp.monad.MonadError

class MyWrapper[F[_], P] private (delegate: SttpBackend[F, P])
  extends SttpBackend[F, P] {

  def send[T, R >: P with Effect[F]](request: Request[T, R]): F[Response[T]] = ???

  def close(): F[Unit] = ???

  def responseMonad: MonadError[F] = ???
}

object MyWrapper {
  def apply[F[_], P](
    delegate: SttpBackend[F, P]): SttpBackend[F, P] = {
    // disables any other FollowRedirectsBackend-s further down the delegate chain
    new FollowRedirectsBackend(new MyWrapper(delegate))
  }
}
```

## Logging backend wrapper

A good example on how to implement a logging backend wrapper is the [logging](logging.md) backend wrapper implementation. It uses the `ListenerBackend` to get notified about request lifecycle events.

To adjust the logs to your needs, or to integrate with your logging framework, simply copy the code and modify as needed. 

## Example metrics backend wrapper

Below is an example on how to implement a backend wrapper, which sends
metrics for completed requests and wraps any `Future`-based backend:

```scala
import sttp.capabilities.Effect
import sttp.client3._
import sttp.client3.akkahttp._
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
class MetricWrapper[P](delegate: SttpBackend[Future, P],
                       metrics: MetricsServer)
    extends DelegateSttpBackend[Future, P](delegate) {

  override def send[T, R >: P with Effect[Future]](request: Request[T, R]): Future[Response[T]] = {
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

// example usage
val backend = new MetricWrapper(
  AkkaHttpBackend(),
  new CloudMetricsServer()
)

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

```scala
import sttp.capabilities.Effect
import sttp.client3._

class RetryingBackend[F[_], P](
    delegate: SttpBackend[F, P],
    shouldRetry: RetryWhen,
    maxRetries: Int)
    extends DelegateSttpBackend[F, P](delegate) {

  override def send[T, R >: P with Effect[F]](request: Request[T, R]): F[Response[T]] = {
    sendWithRetryCounter(request, 0)
  }

  private def sendWithRetryCounter[T, R >: P with Effect[F]](
    request: Request[T, R], retries: Int): F[Response[T]] = {

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
```                    

## Example backend with circuit breaker

> "When a system is seriously struggling, failing fast is better than making clients wait."

There are many libraries that can help you achieve such a behavior: [hystrix](https://github.com/Netflix/Hystrix), [resilience4j](https://github.com/resilience4j/resilience4j), [akka's circuit breaker](https://doc.akka.io/docs/akka/current/common/circuitbreaker.html) or [monix catnap](https://monix.io/docs/3x/catnap/circuit-breaker.html) to name a few. Despite some small differences, both their apis and functionality are very similar, that's why we didn't want to support each of them explicitly.

Below is an example on how to implement a backend wrapper, which integrates with circuit-breaker module from resilience4j library and wraps any backend:

```scala
import io.github.resilience4j.circuitbreaker.{CallNotPermittedException, CircuitBreaker}
import sttp.capabilities.Effect
import sttp.client3.{Request, Response, SttpBackend, DelegateSttpBackend}
import sttp.monad.MonadError
import java.util.concurrent.TimeUnit

class CircuitSttpBackend[F[_], P](
    circuitBreaker: CircuitBreaker,
    delegate: SttpBackend[F, P]) extends DelegateSttpBackend[F, P](delegate) {

  override def send[T, R >: P with Effect[F]](request: Request[T, R]): F[Response[T]] = {
    CircuitSttpBackend.decorateF(circuitBreaker, delegate.send(request))
  }
}

object CircuitSttpBackend {

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

```scala
import io.github.resilience4j.ratelimiter.RateLimiter
import sttp.capabilities.Effect
import sttp.monad.MonadError
import sttp.client3.{Request, Response, SttpBackend, DelegateSttpBackend}

class RateLimitingSttpBackend[F[_], P](
    rateLimiter: RateLimiter,
    delegate: SttpBackend[F, P]
    )(implicit monadError: MonadError[F]) extends DelegateSttpBackend[F, P](delegate) {

  override def send[T, R >: P with Effect[F]](request: Request[T, R]): F[Response[T]] = {
    RateLimitingSttpBackend.decorateF(rateLimiter, delegate.send(request))
  }
}

object RateLimitingSttpBackend {

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
"com.softwaremill.sttp.client3" %% "core" % "3.3.10" % Test classifier "tests"
```

Implement your backend and extend the `HttpTest` class:

```scala
import sttp.client3._
import sttp.client3.testing.{ConvertToFuture, HttpTest}
import scala.concurrent.Future

class MyCustomBackendHttpTest extends HttpTest[Future] {
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
  override lazy val backend: SttpBackend[Future, Any] = ??? //new MyCustomBackend()
  override def timeoutToNone[T](t: Future[T], timeoutMillis: Int): Future[Option[T]] = ???
}
```

You can find a more detailed example in the [sttp-vertx](https://github.com/guymers/sttp-vertx) repository.

## Custom backend wrapper using cats

When implementing a backend wrapper using cats, it might be useful to import:

```scala
import sttp.client3.impl.cats.implicits._
```

from the cats integration module. The module should be available on the classpath when using the cats [async-http-client](../catseffect.md) backend. The object contains implicits to convert a cats `MonadError` into the sttp `MonadError`, as well as a way to map the effects wrapper used with the `.mapK` extension method for the backend. 
