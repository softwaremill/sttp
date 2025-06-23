# Supported backends

sttp supports a number of synchronous and asynchronous backends. It's the backends that take care of managing connections, sending requests and receiving responses: sttp defines only the API to describe the requests to be sent and handle the response data. Backends do all the heavy-lifting. Typically, a single backend instance is created for the lifetime of the application.

Choosing the right backend depends on a number of factors: whether you are using sttp to explore some data, or is it a production system; are you using a synchronous, blocking architecture, or an asynchronous one; do you work mostly with Scala's `Future`, or maybe you use some form of a `Task` abstraction; finally, if you want to stream requests/responses, or not.

## Default backends

As a starting point, the default backends are good choice. Depending on the platform, the following are available:

* on the JVM, `DefaultSyncBackend` and `DefaultFutureBackend`: both based on Java's HTTP client
* on JS, `DefaultFutureBackend`, based on Fetch
* on Native, `DefaultSyncBackend`, based on curl

These default backends provide limited customisation options, hence for any more advanced use-cases, simply substitute them with a specific implementation. E.g. the `HttpClientSyncBackend` backend, which is the underlying implementation of `DefaultSyncBackend`, offers customisation options not available in the default one.

## JVM backends

Which one to choose?

* for simple exploratory requests, use the [synchronous](synchronous.md) `DefaultSyncBackend` / `HttpClientSyncBackend`.
* if you have Akka in your stack, use the [Akka backend](akka.md)
* if you have Pekko in your stack, use the [Pekko backend](pekko.md)
* if you are using `Future` without Akka, use the `DefaultFutureBackend` / `HttpClientFutureBackend`
* finally, if you are using a functional effect wrapper, use one of the "functional" backends, for [ZIO](zio.md), [Monix](monix.md), [Scalaz](scalaz.md), [cats-effect](catseffect.md) or [fs2](fs2.md).

Below is a summary of all the JVM backends; see the sections on individual backend implementations for more information:

```{eval-rst}
==================================== ================================ ============================================================ ========================== ===================
Class                                Effect type                      Supported stream type                                        Supports websockets        Fully non-blocking
==================================== ================================ ============================================================ ========================== ===================
``DefaultSyncBackend``               None (``Identity``)              ``java.io.InputStream`` (blocking) & `ox.flow.Flow`*         yes (regular & streaming*) no
``HttpClientSyncBackend``            None (``Identity``)              ``java.io.InputStream`` (blocking) & `ox.flow.Flow`*         yes (regular & streaming*) no
``DefaultFutureBackend``             ``scala.concurrent.Future``      ``java.io.InputStream`` (blocking)                           yes (regular)              no
``HttpClientFutureBackend``          ``scala.concurrent.Future``      ``java.io.InputStream`` (blocking)                           yes (regular)              no
``HttpClientMonixBackend``           ``monix.eval.Task``              ``monix.reactive.Observable[ByteBuffer]``                    yes (regular & streaming)  yes
``HttpClientFs2Backend``             ``F[_]: cats.effect.Concurrent`` ``fs2.Stream[F, Byte]``                                      yes (regular & streaming)  yes
``HttpClientZioBackend``             ``zio.Task``                     ``zio.stream.Stream[Throwable, Byte]``                       yes (regular & streaming)  yes
``HttpURLConnectionBackend``         None (``Identity``)              ``java.io.InputStream`` (blocking) & `ox.flow.Flow`*         no                         no
``TryHttpURLConnectionBackend``      ``scala.util.Try``               ``java.io.InputStream`` (blocking) & `ox.flow.Flow`*         no                         no
``AkkaHttpBackend``                  ``scala.concurrent.Future``      ``akka.stream.scaladsl.Source[ByteString, Any]``             yes (regular & streaming)  yes
``PekkoHttpBackend``                  ``scala.concurrent.Future``     ``org.apache.pekko.stream.scaladsl.Source[ByteString, Any]`` yes (regular & streaming)  yes
``ArmeriaFutureBackend``             ``scala.concurrent.Future``      n/a                                                          no                         yes
``ArmeriaScalazBackend``             ``scalaz.concurrent.Task``       n/a                                                          no                         yes
``ArmeriaZioBackend``                ``zio.Task``                     ``zio.stream.Stream[Throwable, Byte]``                       no                         yes
``ArmeriaMonixBackend``              ``monix.eval.Task``              ``monix.reactive.Observable[HttpData]``                      no                         yes
``ArmeriaCatsBackend``               ``F[_]: cats.effect.Concurrent`` n/a                                                          no                         yes
``ArmeriaFs2Backend``                ``F[_]: cats.effect.Concurrent`` ``fs2.Stream[F, Byte]``                                      no                         yes
``OkHttpSyncBackend``                None (``Identity``)              ``java.io.InputStream`` (blocking) & `ox.flow.Flow`*         yes (regular & streaming*) no
``OkHttpFutureBackend``              ``scala.concurrent.Future``      ``java.io.InputStream`` (blocking)                           yes (regular)              no
``OkHttpMonixBackend``               ``monix.eval.Task``              ``monix.reactive.Observable[ByteBuffer]``                    yes (regular & streaming)  no
``Http4sBackend``                    ``F[_]: cats.effect.Effect``     ``fs2.Stream[F, Byte]``                                      no                         no
``FinagleBackend``                   ``com.twitter.util.Future``      n/a                                                          no                         no
==================================== ================================ ============================================================ ========================== ===================
```

The backends work with Scala 2.12, 2.13 and 3.

Backends supporting cats-effect are available in versions for cats-effect 2.x (dependency artifacts have the `-ce2` suffix) and 3.x.

All backends that support asynchronous/non-blocking streams, also support server-sent events.

(*) The synchronous backends support streaming & streaming web sockets through Ox `Flow`s, which is available on Java 21+. See 
section on [synchronous backends](synchronous.md) for more details.

## Backend wrappers

There are also backends which wrap other backends to provide additional functionality. These include:

* `TryBackend`, which safely wraps any exceptions thrown by a synchronous backend in `scala.util.Try`
* `EitherBackend`, which represents exceptions as the left side of an `Either`
* `OpenTelemetryTracingBackend`, for OpenTelemetry-compatible distributed tracing. See the [dedicated section](wrappers/opentelemetry.md).
* `OpenTelemetryMetricsBackend`, for OpenTelemetry-compatible metrics. See the [dedicated section](wrappers/opentelemetry.md).
* `PrometheusBackend`, for gathering Prometheus-format metrics. See the [dedicated section](wrappers/prometheus.md).
* extendable logging backends (with an slf4j implementation) backends. See the [dedicated section](wrappers/logging.md).
* `ResolveRelativeUrisBackend` to resolve relative URIs given a base URI, or an arbitrary effectful function
* `ListenerBackend` to listen for backend lifecycle events. See the [dedicated section](wrappers/custom.md).
* `FollowRedirectsBackend`, which handles redirects. All implementation backends are created wrapped with this one.
* `CachingBackend`, which caches responses. See the [dedicated section](wrappers/caching.md).

## Scala.JS backends

In addition, there are also backends for Scala.JS:

```{eval-rst}
================================ ================================ ========================================= ===================
Class                            Effect type                      Supported stream type                     Supports websockets
================================ ================================ ========================================= ===================
``DefaultFutureBackend``         ``scala.concurrent.Future``      n/a                                       yes (regular)
``FetchBackend``                 ``scala.concurrent.Future``      n/a                                       yes (regular)
``FetchMonixBackend``            ``monix.eval.Task``              ``monix.reactive.Observable[ByteBuffer]`` yes (regular & streaming)
``FetchZioBackend``              ``zio.Task``                     ``zio.stream.Stream[Throwable, Byte]``    yes (regular & streaming)
``FetchCatsBackend``             ``F[_]: cats.effect.Concurrent`` n/a                                       yes (regular)
================================ ================================ ========================================= ===================
```

## Scala Native backends

And a backend for scala-native:

```{eval-rst}
================================ ============================ ========================================= ===================
Class                            Effect type                  Supported stream type                     Supports websockets
================================ ============================ ========================================= ===================
``DefaultSyncBackend``           None (``Identity``)          n/a                                       no
``CurlBackend``                  None (``Identity``)          n/a                                       no
================================ ============================ ========================================= ===================
```

## Backend types

Depending on the capabilities that a backend supports, the exact backend type differs:

* `SyncBackend` are backends which are synchronous, blocking, and don't support streaming.
* `Backend[F]` are backends which don't support streaming or web sockets, and use `F` to represent side effects (e.g. obtaining a response for a request)
* `StreamBackend[F, S]` are backends which support streaming, use `F` to represent side effects, and `S` to represent streams of bytes.
* `WebSocketBackend[F]` are backends which support web sockets, use `F` to represent side effects
* `WebSocketStreamBackend[F, S]` are backends which support web sockets and streaming, use `F` to represent side effects, and `S` to represent streams of bytes.

Each backend type extends `GenericBackend` has two type parameters:

* `F[_]`, the type constructor used to represent side effects. That is, when you invoke `send(backend)` on a request description, do you get a `Response[_]` directly, or is it wrapped in a `Future` or a `Task`?
* `P`, the capabilities supported by the backend, in addition to `Effect[F]`. If `Any`, no additional capabilities are provided. Might include `Streams` (the ability to send and receive streaming bodies) and `WebSockets` (the ability to handle websocket requests).
