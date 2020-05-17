# Supported backends

sttp supports a number of synchronous and asynchronous backends. It's the backends that take care of managing connections, sending requests and receiving responses: sttp defines only the API to describe the requests to be send and handle the response data. Backends do all the heavy-lifting.

Choosing the right backend depends on a number of factors: whether you are using sttp to explore some data, or is it a production system; are you using a synchronous, blocking architecture or an asynchronous one; do you work mostly with Scala's `Future`, or maybe you use some form of a `Task` abstraction; finally, if you want to stream requests/responses, or not.

Which one to choose?

* for simple exploratory requests, use the [synchronous](synchronous.html) `HttpURLConnectionBackend`, or `HttpClientSyncBackend` if you are on Java11.
* if you have Akka in your stack, use [Akka backend](akka.html)
* otherwise, if you are using `Future`, use the `AsyncHttpClientFutureBackend` [Future](future.html) backend
* finally, if you are using a functional effect wrapper, use one of the "functional" backends, for [ZIO](zio.html), [Monix](monix.html), [Scalaz](scalaz.html), [cats-effect](catseffect.html) or [fs2](fs2.html). 

Each backend has three type parameters:

* `F[_]`, the effects wrapper for responses. That is, when you invoke `send()` on a request description, do you get a `Response[_]` directly, or is it wrapped in a `Future` or a `Task`?
* `S`, the type of supported streams. If `Nothing`, streaming is not supported. Otherwise, the given type can be used to send request bodies or receive response bodies.
* `WS_HANDLER`, the type of supported websocket handlers. If `NothingT`, websockets are not supported. Otherwise, websocket connections can be opened, given an instance of the handler

Below is a summary of all the JVM backends; see the sections on individual backend implementations for more information:

```eval_rst
==================================== ============================ ================================================ ==================================================
Class                                Response wrapper             Supported stream type                            Supported websocket handlers
==================================== ============================ ================================================ ==================================================
``HttpURLConnectionBackend``         None (``Identity``)          n/a                                              n/a
``TryHttpURLConnectionBackend``      ``scala.util.Try``           n/a                                              n/a
``AkkaHttpBackend``                  ``scala.concurrent.Future``  ``akka.stream.scaladsl.Source[ByteString, Any]`` ``akka.stream.scaladsl.Flow[Message, Message, _]``
``AsyncHttpClientFutureBackend``     ``scala.concurrent.Future``  n/a                                              ``sttp.client.asynchttpclient.WebSocketHandler``
``AsyncHttpClientScalazBackend``     ``scalaz.concurrent.Task``   n/a                                              ``sttp.client.asynchttpclient.WebSocketHandler``
``AsyncHttpClientZioBackend``        ``zio.IO``                   n/a                                              ``sttp.client.asynchttpclient.WebSocketHandler``
``AsyncHttpClientZioStreamsBackend`` ``zio.IO``                   ``zio.stream.Stream[Throwable, ByteBuffer]``     ``sttp.client.asynchttpclient.WebSocketHandler``
``AsyncHttpClientMonixBackend``      ``monix.eval.Task``          ``monix.reactive.Observable[ByteBuffer]``        ``sttp.client.asynchttpclient.WebSocketHandler``
``AsyncHttpClientCatsBackend``       ``F[_]: cats.effect.Async``  n/a                                              ``sttp.client.asynchttpclient.WebSocketHandler``
``AsyncHttpClientFs2Backend``        ``F[_]: cats.effect.Async``  ``fs2.Stream[F, ByteBuffer]``                    ``sttp.client.asynchttpclient.WebSocketHandler``
``OkHttpSyncBackend``                None (``Identity``)          n/a                                              ``sttp.client.okhttp.WebSocketHandler``
``OkHttpFutureBackend``              ``scala.concurrent.Future``  n/a                                              ``sttp.client.okhttp.WebSocketHandler``
``OkHttpMonixBackend``               ``monix.eval.Task``          ``monix.reactive.Observable[ByteBuffer]``        ``sttp.client.okhttp.WebSocketHandler``
``Http4sBackend``                    ``F[_]: cats.effect.Effect`` ``fs2.Stream[F, Byte]``                          n/a
``HttpClientSyncBackend``            None (``Identity``)          n/a                                              ``sttp.client.httpclient.WebSocketHandler``
``HttpClientFutureBackend``          ``scala.concurrent.Future``  n/a                                              ``sttp.client.httpclient.WebSocketHandler``
``HttpClientMonixBackend``           ``monix.eval.Task``          ``monix.reactive.Observable[ByteBuffer]``        ``sttp.client.httpclient.WebSocketHandler``
``FinagleBackend``                   ``com.twitter.util.Future``  n/a                                              n/a
==================================== ============================ ================================================ ==================================================
```

The backends work with Scala 2.11, 2.12 and 2.13 (with some exceptions for 2.11). Moreover, `HttpURLConnectionBackend`, `AsyncHttpClientFutureBackend`, `AsyncHttpClientZioBackend`, `HttpClientSyncBackend` and `HttpClientFutureBackend` are additionally built with Dotty (Scala 3).

There are also backends which wrap other backends to provide additional functionality. These include:

* `TryBackend`, which safely wraps any exceptions thrown by a synchronous backend in `scala.util.Try`
* `OpenTracingBackend`, for OpenTracing-compatible distributed tracing. See the [dedicated section](wrappers/opentracing.html).
* `BraveBackend`, for Zipkin-compatible distributed tracing. See the [dedicated section](wrappers/brave.html).
* `PrometheusBackend`, for gathering Prometheus-format metrics. See the [dedicated section](wrappers/prometheus.html).
* slf4j backends, for logging. See the [dedicated section](wrappers/slf4j.html).

In additional there are also backends for JavaScript:

```eval_rst
================================ ============================ ========================================= ============================
Class                            Response wrapper             Supported stream type                     Supported websocket handlers
================================ ============================ ========================================= ============================
``FetchBackend``                 ``scala.concurrent.Future``  n/a                                       n/a
``FetchMonixBackend``            ``monix.eval.Task``          ``monix.reactive.Observable[ByteBuffer]`` n/a
================================ ============================ ========================================= ============================
```

And a backend for scala-native:

```eval_rst
================================ ============================ ========================================= ============================
Class                            Response wrapper             Supported stream type                     Supported websocket handlers
================================ ============================ ========================================= ============================
``CurlBackend``                  None (``Identity``)          n/a                                       n/a
================================ ============================ ========================================= ============================
```

Finally, there are third-party backends:

* [sttp-play-ws](https://github.com/ragb/sttp-play-ws) for "standard" play-ws (not standalone).
* [akkaMonixSttpBackend](https://github.com/fullfacing/akkaMonixSttpBackend), an Akka-based backend, but using Monix's `Task` & `Observable`.
