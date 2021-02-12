# Supported backends

sttp supports a number of synchronous and asynchronous backends. It's the backends that take care of managing connections, sending requests and receiving responses: sttp defines only the API to describe the requests to be send and handle the response data. Backends do all the heavy-lifting.

Choosing the right backend depends on a number of factors: whether you are using sttp to explore some data, or is it a production system; are you using a synchronous, blocking architecture or an asynchronous one; do you work mostly with Scala's `Future`, or maybe you use some form of a `Task` abstraction; finally, if you want to stream requests/responses, or not.

Which one to choose?

* for simple exploratory requests, use the [synchronous](synchronous.md) `HttpURLConnectionBackend`, or `HttpClientSyncBackend` if you are on Java11+.
* if you have Akka in your stack, use the [Akka backend](akka.md)
* otherwise, if you are using `Future`, use `AsyncHttpClientFutureBackend` [Future](future.md) backend, or `HttpClientFutureBackend` if you are on Java11+.
* finally, if you are using a functional effect wrapper, use one of the "functional" backends, for [ZIO](zio.md), [Monix](monix.md), [Scalaz](scalaz.md), [cats-effect](catseffect.md) or [fs2](fs2.md). 

Each backend has two type parameters:

* `F[_]`, the effects wrapper for responses. That is, when you invoke `send(backend)` on a request description, do you get a `Response[_]` directly, or is it wrapped in a `Future` or a `Task`?
* `P`, the capabilities supported by the backend, in addition to `Effect[F]`. If `Any`, no additional capabilities are provided. Might include `Streams` (the ability to send and receive streaming bodies) and `WebSockets` (the ability to handle websocket requests).

Below is a summary of all the JVM backends; see the sections on individual backend implementations for more information:

```eval_rst
==================================== ================================ ================================================= ========================== =================== ===============================
Class                                Effect type                      Supported stream type                             Supports websockets        Fully non-blocking  Supports host header overrides 
==================================== ================================ ================================================= ========================== =================== ===============================
``HttpURLConnectionBackend``         None (``Identity``)              n/a                                               no                         no                  no
``TryHttpURLConnectionBackend``      ``scala.util.Try``               n/a                                               no                         no                  no
``AkkaHttpBackend``                  ``scala.concurrent.Future``      ``akka.stream.scaladsl.Source[ByteString, Any]``  yes (regular & streaming)  yes                 yes 
``AsyncHttpClientFutureBackend``     ``scala.concurrent.Future``      n/a                                               yes (regular)              no                  no
``AsyncHttpClientScalazBackend``     ``scalaz.concurrent.Task``       n/a                                               yes (regular)              no                  yes          
``AsyncHttpClientZioBackend``        ``zio.Task``                     ``zio.stream.Stream[Throwable, Byte]``            yes (regular & streaming)  no                  yes
``AsyncHttpClientMonixBackend``      ``monix.eval.Task``              ``monix.reactive.Observable[ByteBuffer]``         yes (regular & streaming)  no                  yes 
``AsyncHttpClientCatsBackend``       ``F[_]: cats.effect.Concurrent`` n/a                                               no                         no                  yes
``AsyncHttpClientFs2Backend``        ``F[_]: cats.effect.Concurrent`` ``fs2.Stream[F, Byte]``                           yes (regular & streaming)  no                  yes 
``OkHttpSyncBackend``                None (``Identity``)              n/a                                               yes (regular)              no                  yes
``OkHttpFutureBackend``              ``scala.concurrent.Future``      n/a                                               yes (regular)              no                  yes 
``OkHttpMonixBackend``               ``monix.eval.Task``              ``monix.reactive.Observable[ByteBuffer]``         yes (regular & streaming)  no                  yes
``Http4sBackend``                    ``F[_]: cats.effect.Effect``     ``fs2.Stream[F, Byte]``                           no                         no                  yes 
``HttpClientSyncBackend``            None (``Identity``)              n/a                                               no                         no                  no
``HttpClientFutureBackend``          ``scala.concurrent.Future``      n/a                                               yes (regular)              no                  no
``HttpClientMonixBackend``           ``monix.eval.Task``              ``monix.reactive.Observable[ByteBuffer]``         yes (regular & streaming)  yes                 no 
``HttpClientFs2Backend``             ``F[_]: cats.effect.Concurrent`` ``fs2.Stream[F, Byte]``                           yes (regular & streaming)  yes                 no                             
``HttpClientZioBackend``             ``zio.Task``                     ``zio.stream.Stream[Throwable, Byte]``            yes (regular & streaming)  yes                 no 
``FinagleBackend``                   ``com.twitter.util.Future``      n/a                                               no                         no                  yes 
``ArmeriaBackend``                   ``scala.concurrent.Future``      n/a                                               no                         yes                 no 
==================================== ================================ ================================================= ========================== =================== ===============================
```

The backends work with Scala 2.11, 2.12 and 2.13 (with some exceptions for 2.11). Moreover, `HttpURLConnectionBackend`, `AsyncHttpClientFutureBackend`, `AsyncHttpClientZioBackend`, `HttpClientSyncBackend`, `HttpClientFutureBackend` and `HttpClientZioBackend` are additionally built with Scala 3.

All backends that support asynchronous/non-blocking streams, also support server-sent events.

There are also backends which wrap other backends to provide additional functionality. These include:

* `TryBackend`, which safely wraps any exceptions thrown by a synchronous backend in `scala.util.Try`
* `OpenTracingBackend`, for OpenTracing-compatible distributed tracing. See the [dedicated section](wrappers/opentracing.md).
* `PrometheusBackend`, for gathering Prometheus-format metrics. See the [dedicated section](wrappers/prometheus.md).
* extendable logging backends (with an slf4j implementation) backends. See the [dedicated section](wrappers/logging.md).
* `ResolveRelativeUrisBackend` to resolve relative URIs given a base URI, or an arbitrary effectful function
* `ListenerBackend` to listen for backend lifecycle events. See the [dedicated section](wrappers/custom.md).
* `FollowRedirectsBackend`, which handles redirects. All implementation backends are created wrapped with this one.

In addition, there are also backends for Scala.JS:

```eval_rst
================================ ================================ ========================================= ===================
Class                            Effect type                      Supported stream type                     Supports websockets
================================ ================================ ========================================= ===================
``FetchBackend``                 ``scala.concurrent.Future``      n/a                                       no
``FetchMonixBackend``            ``monix.eval.Task``              ``monix.reactive.Observable[ByteBuffer]`` no
``FetchCatsBackend``             ``F[_]: cats.effect.Concurrent`` n/a                                       no
================================ ================================ ========================================= ===================
```

And a backend for scala-native:

```eval_rst
================================ ============================ ========================================= ===================
Class                            Effect type                  Supported stream type                     Supports websockets
================================ ============================ ========================================= ===================
``CurlBackend``                  None (``Identity``)          n/a                                       no
================================ ============================ ========================================= ===================
```

Finally, there are third-party backends:

* [sttp-play-ws](https://github.com/ragb/sttp-play-ws) for "standard" play-ws (not standalone).
* [akkaMonixSttpBackend](https://github.com/fullfacing/akkaMonixSttpBackend), an Akka-based backend, but using Monix's `Task` & `Observable`.
