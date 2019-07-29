.. _backends_summary:

Supported backends
==================

sttp supports a number of synchronous and asynchronous backends. It's the backends that take care of managing connections, sending requests and receiving responses: sttp defines only the API to describe the requests to be send and handle the response data. It's the backends where all the heavy-lifting is done.

Choosing the right backend depends on a number of factors: if you are using sttp to explore some data, or is it a production system; are you using a synchronous, blocking architecture or an asynchronous one; do you work mostly with Scala's ``Future``, or maybe you use some form of a ``Task`` abstraction; finally, if you want to stream requests/responses, or not.

Each backend has two type parameters:

* ``R[_]``, the type constructor in which responses are wrapped. That is, when you invoke ``send()`` on a request description, do you get a ``Response[_]`` directly, or is it wrapped in a ``Future`` or a ``Task``?
* ``S``, the type of supported streams. If ``Nothing``, streaming is not supported. Otherwise, the given type can be used to send request bodies or receive response bodies.

Below is a summary of all the backends. See the sections on individual backend implementations for more information.

==================================== ============================ ================================================
Class                                Response wrapper             Supported stream type
==================================== ============================ ================================================
``HttpURLConnectionBackend``         None (``Id``)                n/a
``TryHttpURLConnectionBackend``      ``scala.util.Try``           n/a
``AkkaHttpBackend``                  ``scala.concurrent.Future``  ``akka.stream.scaladsl.Source[ByteString, Any]``
``AsyncHttpClientFutureBackend``     ``scala.concurrent.Future``  n/a
``AsyncHttpClientScalazBackend``     ``scalaz.concurrent.Task``   n/a
``AsyncHttpClientZioBackend``        ``zio.IO``                   n/a
``AsyncHttpClientZioStreamsBackend`` ``zio.IO``                   ``Stream[Throwable, ByteBuffer]``
``AsyncHttpClientMonixBackend``      ``monix.eval.Task``          ``monix.reactive.Observable[ByteBuffer]``
``AsyncHttpClientCatsBackend``       ``F[_]: cats.effect.Async``  n/a
``AsyncHttpClientFs2Backend``        ``F[_]: cats.effect.Async``  ``fs2.Stream[F, ByteBuffer]``
``OkHttpSyncBackend``                None (``Id``)                n/a
``OkHttpFutureBackend``              ``scala.concurrent.Future``  n/a
``OkHttpMonixBackend``               ``monix.eval.Task``          ``monix.reactive.Observable[ByteBuffer]``
==================================== ============================ ================================================

There are also backends which wrap other backends to provide additional functionality. These include:

* ``TryBackend``, which safely wraps any exceptions thrown by a synchronous backend in ``scala.util.Try``
* ``BraveBackend``, for Zipkin-compatible distributed tracing. See the :ref:`dedicated section <brave_backend>`.
* ``PrometheusBackend``, for gathering Prometheus-format metrics. See the :ref:`dedicated section <prometheus_backend>`.

In additional there are also backends for JavaScript:

================================ ============================ =========================================
Class                            Response wrapper             Supported stream type
================================ ============================ =========================================
``FetchBackend``                 ``scala.concurrent.Future``  n/a
``FetchMonixBackend``            ``monix.eval.Task``          ``monix.reactive.Observable[ByteBuffer]``
================================ ============================ =========================================

and Scala Native:

================================ ============================ =========================================
Class                            Response wrapper             Supported stream type
================================ ============================ =========================================
``CurlBackend``                  None (``id``)                n/a
``CurlTryBackend``               ``scala.util.Try``           n/a
================================ ============================ =========================================

Finally, there are third-party backends:

* `sttp-play-ws <https://github.com/ragb/sttp-play-ws>`_ for "standard" play-ws (not standalone).
* `akkaMonixSttpBackend <https://github.com/fullfacing/akkaMonixSttpBackend>`_, an Akka-based backend, but using Monix's ``Task``s/``Observable``s.
