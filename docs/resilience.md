# Resilience

Resilience covers areas such as retries, circuit breaking and rate limiting.

sttp client doesn't have the above built-in, as these concepts are usually best handled on a higher level. Sending a request (that is, invoking `myRequest.send(backend)`), can be viewed as a:

* `() => Response[T]` function for synchronous backends
* `() => Future[Response[T]]` for `Future`-based asynchronous backends
* `IO[Response[T]]`/`Task[Response[T]]` process description

All of these are lazily evaluated, and can be repeated. Such a representation allows to integrate the `send()` side-effect with a stack-dependent resilience tool. There's a number of libraries that implement the above mentioned resilience functionalities, hence there's no sense for sttp client to reimplement any of those. That's simply not the scope of this library.

Still, the input for a particular resilience model might involve both the result (either an exception, or a response) and the original description of the request being sent. E.g. retries can depend on the request method; circuit-breaking can depend on the host, to which the request is sent; same for rate limiting.

## Retries

Here's an incomplete list of libraries which can be used to manage retries in various Scala stacks:

* for `Future`: [retry](https://github.com/softwaremill/retry)
* for ZIO: [schedules](https://zio.dev/docs/datatypes/datatypes_schedule), [rezilience](https://github.com/svroonland/rezilience)
* for Monix/cats-effect: [cats-retry](https://github.com/cb372/cats-retry)
* for Monix: `.restart` methods

sttp client contains a default implementation of a predicate, which allows deciding if a request is retriable: if the body can be sent multiple times, and if the HTTP method is idempotent.
This predicate is available as `RetryWhen.Default` and has type `(Request[_, _], Either[Throwable, Response[_]]) => Boolean`.

See also the [retrying using ZIO](examples.html#retry-a-request-using-zio) example, as well as an example of a very simple [retrying backend wrapper](backends/wrappers/custom.html#example-retrying-backend-wrapper). 

### Backend-specific retries

Some backends have built-in retry mechanisms:

* [akka-http](https://doc.akka.io/docs/akka-http/current/scala/http/client-side/host-level.html#retrying-a-request)
* [OkHttp](http://square.github.io/okhttp) (see the builder's `retryOnConnectionFailure` method)
* async-http-client (deprecated): by default, the backend will attempt 5 retries in case an `IOException` is thrown during the connection. This can be changed by specifying the `org.asynchttpclient.maxRequestRetry` config option, or by providing custom configuration using when creating the backend (`setMaxRequestRetry`).

## Circuit breaking 

* for Monix & cats-effect: [monix-catnap](https://monix.io/docs/3x/#monix-catnap)
* for Akka/`Future`: [akka circuit breaker](https://doc.akka.io/docs/akka/current/common/circuitbreaker.html)
* for ZIO: [rezilience](https://github.com/svroonland/rezilience)

## Rate limiting

* for akka-streams: [throttle in akka streams](https://doc.akka.io/docs/akka/current/stream/operators/Source-or-Flow/throttle.html)
* for ZIO: [rezilience](https://github.com/svroonland/rezilience)

## Java libraries

* [resilience4j](https://github.com/resilience4j/resilience4j)
