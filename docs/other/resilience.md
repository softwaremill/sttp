# Resilience

Resilience covers areas such as retries, circuit breaking and rate limiting.

sttp client doesn't have the above built-in, as these concepts are usually best handled on a higher level. Sending a request (that is, invoking `myRequest.send(backend)`), can be viewed as a:

* `() => Response[T]` function for synchronous backends
* `() => Future[Response[T]]` for `Future`-based asynchronous backends
* `IO[Response[T]]`/`Task[Response[T]]` process description

All of these are lazily evaluated, and can be repeated. Such a representation allows to integrate the `send()` side-effect with a stack-dependent resilience tool. There's a number of libraries that implement the above mentioned resilience functionalities, hence there's no sense for sttp client to reimplement any of those. That's simply not the scope of this library.

Still, the input for a particular resilience model might involve both the result (either an exception, or a response) and the original description of the request being sent. E.g. retries can depend on the request method; circuit-breaking can depend on the host, to which the request is sent; same for rate limiting.

## Retries

Handling retries is a complex problem when it comes to HTTP requests. When is a request retryable? There are a couple of things to take into account:

* connection exceptions are generally good candidates for retries
* only idempotent HTTP methods (such as `GET`) could potentially be retried
* some HTTP status codes might also be retryable (e.g. `500 Internal Server Error` or `503 Service Unavailable`)

In some cases it's possible to implement a generic retry mechanism; such a mechanism should take into account logging, metrics, limiting the number of retries and a backoff mechanism. These mechanisms could be quite simple, or involve e.g. retry budgets (see [Finagle's](https://twitter.github.io/finagle/guide/Clients.html#retries) documentation on retries). In sttp, it's possible to recover from errors using the `monad`. 

sttp client contains a default implementation of a predicate, which allows deciding if a request is retriable: if the body can be sent multiple times, and if the HTTP method is idempotent. This predicate is available as `RetryWhen.Default` and has type `(GenericRequest[_, _], Either[Throwable, Response[_]]) => Boolean`.

Here's an incomplete list of libraries which can be used to manage retries in various Scala stacks:

* for synchornous/direct-style: [Ox](https://github.com/softwaremill/ox)
* for `Future`: [retry](https://github.com/softwaremill/retry)
* for ZIO: [schedules](https://zio.dev/reference/schedule/), [rezilience](https://github.com/svroonland/rezilience)
* for Monix/cats-effect: [cats-retry](https://github.com/cb372/cats-retry)
* for Monix: `.restart` methods

See also the "resiliency" and "backend wrapper" [examples](../examples.md). 

### Backend-specific retries

Some backends have built-in retry mechanisms:

* [akka-http](https://doc.akka.io/docs/akka-http/current/scala/http/client-side/host-level.html#retrying-a-request)
* [OkHttp](http://square.github.io/okhttp) (see the builder's `retryOnConnectionFailure` method)

## Circuit breaking 

* for Monix & cats-effect: [monix-catnap](https://monix.io/docs/3x/#monix-catnap)
* for Akka/`Future`: [akka circuit breaker](https://doc.akka.io/docs/akka/current/common/circuitbreaker.html)
* for ZIO: [rezilience](https://github.com/svroonland/rezilience)

## Rate limiting

* for synchornous/direct-style: [Ox](https://github.com/softwaremill/ox)
* for Akka Streams: [throttle in akka streams](https://doc.akka.io/docs/akka/current/stream/operators/Source-or-Flow/throttle.html)
* for ZIO: [rezilience](https://github.com/svroonland/rezilience)

## Java libraries

* [resilience4j](https://github.com/resilience4j/resilience4j) (rate limiting, circuit breaking, retries, other resilience patterns)
