# Custom backends, logging, metrics

It is also entirely possible to write custom backends (if doing so, please consider contributing!) or wrap an existing one. One can even write completely generic wrappers for any delegate backend, as each backend comes equipped with a monad for the response type. This brings the possibility to `map` and `flatMap` over responses.

Possible use-cases for wrapper-backend include:

* logging
* capturing metrics
* request signing (transforming the request before sending it to the delegate)

See also the section on [resilience](../../resilience.html) which covers topics such as retries, circuit breaking and rate limiting.

## Request tagging

Each request contains a `tags: Map[String, Any]` map. This map can be used to tag the request with any backend-specific information, and isn't used in any way by sttp itself.

Tags can be added to a request using the `def tag(k: String, v: Any)` method, and read using the `def tag(k: String): Option[Any]` method.

Backends, or backend wrappers can use tags e.g. for logging, passing a metric name, using different connection pools, or even different delegate backends.

## Listener backend

The `sttp.client.listener.ListenerBackend` can make it easier to create backend wrappers which need to be notified about request lifecycle events: when a request is started, and when it completes either successfully or with an exception. This is possible by implementing a `sttp.client.listener.RequestListener`. This is how e.g. the [slf4j backend](slf4j.html) is implemented. 

A request listener can associate a value with a request, which will then be passed to the request completion notification methods.

A side-effecting request listener, of type `RequestListener[Identity, L]`, can be lifted to a request listener `RequestListener[F, L]` given a `MonadError[F]`, using the `RequestListener.lift` method.

## Backend wrappers and redirects

By default redirects are handled at a low level, using a wrapper around the main, concrete backend: each of the backend factory methods, e.g. `HttpURLConnectionBackend()` returns a backend wrapped in `FollowRedirectsBackend`.

This causes any further backend wrappers to handle a request which involves redirects as one whole, without the intermediate requests. However, wrappers which collects metrics, implements tracing or handles request retries might want to handle every request in the redirect chain. This can be achieved by layering another `FollowRedirectsBackend` on top of the wrapper. Only the top-level follow redirects backend will handle redirects, other follow redirect wrappers (at lower levels) will be disabled.

For example:

```scala
class MyWrapper[F[_], S, WS_HANDLER[_]] private (delegate: SttpBackend[F, S, WS_HANDLER])
  extends SttpBackend[R, S, WS_HANDLER] {

  ...
}

object MyWrapper {
  def apply[F[_], S, WS_HANDLER[_]](
    delegate: SttpBackend[F, S, WS_HANDLER]): SttpBackend[F, S, WS_HANDLER] = {
    // disables any other FollowRedirectsBackend-s further down the delegate chain
    new FollowRedirectsBackend(new MyWrapper(delegate))
  }
}
```

## Logging backend wrapper

A good example on how to implement a logging backend wrapper is the [slf4j](slf4j.html) backend wrapper implementation. It uses the `ListenerBackend` to get notified about request lifecycle events, and logs messages created using `sttp.client.logging.LogMessages`.

To adjust the logs to your needs, or to integrate with your logging framework, simply copy the code and modify as needed. 

## Example metrics backend wrapper

Below is an example on how to implement a backend wrapper, which sends
metrics for completed requests and wraps any `Future`-based backend:

```scala
// the metrics infrastructure
trait MetricsServer {
  def reportDuration(name: String, duration: Long): Unit
}

class CloudMetricsServer extends MetricsServer {
  override def reportDuration(name: String, duration: Long): Unit = ???
}

// the backend wrapper
class MetricWrapper[S](delegate: SttpBackend[Future, S, NothingT],
                       metrics: MetricsServer)
    extends SttpBackend[Future, S, NothingT] {

  override def send[T](request: Request[T, S]): Future[Response[T]] = {
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

  override def openWebsocket[T, WS_RESULT](
      request: Request[T, S],
      handler: NothingT[WS_RESULT]
    ): Future[WebSocketResponse[WS_RESULT]] = {
    delegate.openWebsocket(request, handler) // No websocket support due to NothingT
  }

  override def close(): F[Unit] = delegate.close()

  override def responseMonad: MonadError[Future] = delegate.responseMonad
}

// example usage
implicit val backend = new MetricWrapper(
  AkkaHttpBackend(),
  new CloudMetricsServer()
)

basicRequest
  .get(uri"http://company.com/api/service1")
  .tag("metric", "service1")
  .send()
```

See also the [Prometheus](prometheus.html) backend for an example implementation.

## Example retrying backend wrapper

Handling retries is a complex problem when it comes to HTTP requests. When is a request retryable? There are a couple of things to take into account:

* connection exceptions are generally good candidates for retries
* only idempotent HTTP methods (such as `GET`) could potentially be retried
* some HTTP status codes might also be retryable (e.g. `500 Internal Server Error` or `503 Service Unavailable`)

In some cases it's possible to implement a generic retry mechanism; such a mechanism should take into account logging, metrics, limiting the number of retries and a backoff mechanism. These mechanisms could be quite simple, or involve e.g. retry budgets (see [Finagle's](https://twitter.github.io/finagle/guide/Clients.html#retries) documentation on retries). In sttp, it's possible to recover from errors using the `responseMonad`. A starting point for a retrying backend could be:

```scala
import sttp.client.{MonadError, Request, Response, SttpBackend, RetryWhen}

class RetryingBackend[F[_], S](
    delegate: SttpBackend[F, S, NothingT],
    shouldRetry: RetryWhen,
    maxRetries: Int)
    extends SttpBackend[F, S, NothingT] {

  override def send[T](request: Request[T, S]): F[Response[T]] = {
    sendWithRetryCounter(request, 0)
  }

  private def sendWithRetryCounter[T](request: Request[T, S],
                                      retries: Int): F[Response[T]] = {
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

  override def openWebsocket[T, WS_RESULT](
      request: Request[T, S],
      handler: NothingT[WS_RESULT]
    ): Future[WebSocketResponse[WS_RESULT]] = {
    delegate.openWebsocket(request, handler) // No websocket support due to NothingT
  }

  override def close(): F[Unit] = delegate.close()

  override def responseMonad: MonadError[F] = delegate.responseMonad
}
```                    

## Example backend with circuit breaker

> "When a system is seriously struggling, failing fast is better than making clients wait."

There are many libraries that can help you achieve such a behavior: [hystrix](https://github.com/Netflix/Hystrix), [resilience4j](https://github.com/resilience4j/resilience4j), [akka's circuit breaker](https://doc.akka.io/docs/akka/current/common/circuitbreaker.html) or [monix catnap](https://monix.io/docs/3x/catnap/circuit-breaker.html) to name a few. Despite some small differences, both their apis and functionality are very similar, that's why we didn't want to support each of them explicitly.

Below is an example on how to implement a backend wrapper, which integrates with circuit-breaker module from resilience4j library and wraps any backend:

```scala
import io.github.resilience4j.circuitbreaker.{CallNotPermittedException, CircuitBreaker}
import sttp.client.monad.MonadError
import sttp.client.ws.WebSocketResponse
import sttp.client.{Request, Response, SttpBackend}
import java.util.concurrent.TimeUnit

class CircuitSttpBackend[F[_], S, W[_]](
    circuitBreaker: CircuitBreaker,
    delegate: SttpBackend[F, S, W]
    )(implicit monadError: MonadError[F]) extends SttpBackend[F, S, W] {

  override def send[T](request: Request[T, S]): F[Response[T]] = {
    CircuitSttpBackend.decorateF(circuitBreaker, delegate.send(request))
  }

  override def openWebsocket[T, WS_RESULT](
      request: Request[T, S],
      handler: W[WS_RESULT]
  ): F[WebSocketResponse[WS_RESULT]] =
        CircuitSttpBackend.decorateF(delegate.openWebsocket(request, handler))

  override def close(): F[Unit] = delegate.close()

  override def responseMonad: MonadError[F] = delegate.responseMonad
}

object CircuitSttpBackend {

  def decorateF[F[_], T](
      circuitBreaker: CircuitBreaker,
      service: => F[T]
  )(implicit monadError: MonadError[F]): F[T] = {
    monadError.flatMap(monadError.unit(())) { _ =>
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
import sttp.client.monad.MonadError
import sttp.client.ws.WebSocketResponse
import sttp.client.{Request, Response, SttpBackend}

class RateLimitingSttpBackend[F[_], S, W[_]](
    rateLimiter: RateLimiter,
    delegate: SttpBackend[F, S, W]
    )(implicit monadError: MonadError[F]) extends SttpBackend[F, S, W] {

  override def send[T](request: Request[T, S]): F[Response[T]] = {
    RateLimitingSttpBackend.decorateF(rateLimiter, delegate.send(request))
  }

  override def openWebsocket[T, WS_RESULT](
      request: Request[T, S],
      handler: W[WS_RESULT]
  ): F[WebSocketResponse[WS_RESULT]] = delegate.openWebsocket(request, handler)

  override def close(): F[Unit] = delegate.close()

  override def responseMonad: MonadError[F] = delegate.responseMonad
}

object RateLimitingSttpBackend {

  def decorateF[F[_], T](
      rateLimiter: RateLimiter,
      service: => F[T]
  )(implicit monadError: MonadError[F]): F[T] = {
    monadError.flatMap(monadError.unit(())){ _=>
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
"com.softwaremill.sttp.client" %% "core" % "2.0.6" % Test classifier "tests"
```

Implement your backend and extend the `HttpTest` class:

```scala
import sttp.client.SttpBackend
import sttp.client.testing.{ConvertToFuture, HttpTest}

class MyCustomBackendHttpTest extends HttpTest[Future] {
  override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
  override implicit lazy val backend: SttpBackend[Future, Nothing, NothingT] = new MyCustomBackend()
}
```

You can find a more detailed example in the [sttp-vertx](https://github.com/guymers/sttp-vertx) repository.

## Custom backend wrapper using cats

When implementing a backend wrapper using cats, it might be useful to import:

```scala
import sttp.client.impl.cats.implicits._
```

from the cats integration module. The module should be available on the classpath when using the cats [async-http-client](asynchttpclient.html) backend. The object contains implicits to convert a cats `MonadError` into the sttp `MonadError`, as well as a way to map the effects wrapper used with the `.mapK` extension method for the backend. 