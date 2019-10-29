.. _backends_summary:

Supported backends
==================

sttp supports a number of synchronous and asynchronous backends. It's the backends that take care of managing connections, sending requests and receiving responses: sttp defines only the API to describe the requests to be send and handle the response data. It's the backends where all the heavy-lifting is done.

Choosing the right backend depends on a number of factors: if you are using sttp to explore some data, or is it a production system; are you using a synchronous, blocking architecture or an asynchronous one; do you work mostly with Scala's ``Future``, or maybe you use some form of a ``Task`` abstraction; finally, if you want to stream requests/responses, or not.

Which one to choose?

* for simple exploratory requests, use the synchronous ``HttpURLConnectionBackend``
* if you have Akka in your stack, use ``AkkaHttpBackend``
* otherwise, if you are using ``Future``, use ``AsyncHttpClientFutureBackend``
* finally, if you are using a functional effect wrapper, use one of the "functional" async-http-client backends

Each backend has three type parameters:

* ``F[_]``, the effects wrapper for responses. That is, when you invoke ``send()`` on a request description, do you get a ``Response[_]`` directly, or is it wrapped in a ``Future`` or a ``Task``?
* ``S``, the type of supported streams. If ``Nothing``, streaming is not supported. Otherwise, the given type can be used to send request bodies or receive response bodies.
* ``WS_HANDLER``, the type of supported websocket handlers. If ``NothingT``, websockets are not supported. Otherwise, websocket connections can be opened, given an instance of the handler

Below is a summary of all the backends. See the sections on individual backend implementations for more information.

==================================== ============================ ================================================ ==================================================
Class                                Response wrapper             Supported stream type                            Supported websocket handlers
==================================== ============================ ================================================ ==================================================
``HttpURLConnectionBackend``         None (``Id``)                n/a                                              n/a
``TryHttpURLConnectionBackend``      ``scala.util.Try``           n/a                                              n/a
``AkkaHttpBackend``                  ``scala.concurrent.Future``  ``akka.stream.scaladsl.Source[ByteString, Any]`` ``akka.stream.scaladsl.Flow[Message, Message, _]``
``AsyncHttpClientFutureBackend``     ``scala.concurrent.Future``  n/a                                              ``sttp.client.asynchttpclient.WebSocketHandler``
``AsyncHttpClientScalazBackend``     ``scalaz.concurrent.Task``   n/a                                              ``sttp.client.asynchttpclient.WebSocketHandler``
``AsyncHttpClientZioBackend``        ``zio.IO``                   n/a                                              ``sttp.client.asynchttpclient.WebSocketHandler``
``AsyncHttpClientZioStreamsBackend`` ``zio.IO``                   ``zio.stream.Stream[Throwable, ByteBuffer]``     n/a
``AsyncHttpClientMonixBackend``      ``monix.eval.Task``          ``monix.reactive.Observable[ByteBuffer]``        ``sttp.client.asynchttpclient.WebSocketHandler``
``AsyncHttpClientCatsBackend``       ``F[_]: cats.effect.Async``  n/a                                              ``sttp.client.asynchttpclient.WebSocketHandler``
``AsyncHttpClientFs2Backend``        ``F[_]: cats.effect.Async``  ``fs2.Stream[F, ByteBuffer]``                    ``sttp.client.asynchttpclient.WebSocketHandler``
``OkHttpSyncBackend``                None (``Id``)                n/a                                              ``sttp.client.okhttp.WebSocketHandler``
``OkHttpFutureBackend``              ``scala.concurrent.Future``  n/a                                              ``sttp.client.okhttp.WebSocketHandler``
``OkHttpMonixBackend``               ``monix.eval.Task``          ``monix.reactive.Observable[ByteBuffer]``        ``sttp.client.okhttp.WebSocketHandler``
``Http4sBackend``                    ``F[_]: cats.effect.Effect`` ``fs2.Stream[F, Byte]``                          n/a
``HttpClientSyncBackend``            None (``Id``)                n/a                                              ``sttp.client.httpclient.WebSocketHandler``
``HttpClientFutureBackend``          ``scala.concurrent.Future``  n/a                                              ``sttp.client.httpclient.WebSocketHandler``
``HttpClientMonixBackend``           ``monix.eval.Task``          ``monix.reactive.Observable[ByteBuffer]``        ``sttp.client.httpclient.WebSocketHandler``
==================================== ============================ ================================================ ==================================================

There are also backends which wrap other backends to provide additional functionality. These include:

* ``TryBackend``, which safely wraps any exceptions thrown by a synchronous backend in ``scala.util.Try``
* ``BraveBackend``, for Zipkin-compatible distributed tracing. See the :ref:`dedicated section <brave_backend>`.
* ``PrometheusBackend``, for gathering Prometheus-format metrics. See the :ref:`dedicated section <prometheus_backend>`.

In additional there are also backends for JavaScript:

================================ ============================ ========================================= ============================
Class                            Response wrapper             Supported stream type                     Supported websocket handlers
================================ ============================ ========================================= ============================
``FetchBackend``                 ``scala.concurrent.Future``  n/a                                       n/a
``FetchMonixBackend``            ``monix.eval.Task``          ``monix.reactive.Observable[ByteBuffer]`` n/a
================================ ============================ ========================================= ============================

and Scala Native:

================================ ============================ ========================================= ============================
Class                            Response wrapper             Supported stream type                     Supported websocket handlers
================================ ============================ ========================================= ============================
``CurlBackend``                  None (``id``)                n/a                                       n/a
``CurlTryBackend``               ``scala.util.Try``           n/a                                       n/a
================================ ============================ ========================================= ============================

Finally, there are third-party backends:

* `sttp-play-ws <https://github.com/ragb/sttp-play-ws>`_ for "standard" play-ws (not standalone).
* `akkaMonixSttpBackend <https://github.com/fullfacing/akkaMonixSttpBackend>`_, an Akka-based backend, but using Monix's ``Task`` & ``Observable``.
