.. _backends_summary:

Supported backends
==================

sttp suports a number of synchornous and asynchronous backends. It's the backends that take care of managing connections, sending requests and receiving responses: sttp defines only the API to describe the requests to be send and handle the response data. It's the backends where all the heavy-lifting is done.

Choosing the right backend depends on a number of factors: if you are using sttp to explore some data, or is it a production system; are you using a synchronous, blocking architecture or an asynchronous one; do you work mostly with Scala's ``Future``, or maybe you use some form of a ``Task`` abstraction; finally, if you want to stream requests/responses, or not.

Each backend has two type parameters:

* ``R[_]``, the type constructor in which responses are wrapped. That is, when you invoke ``send()`` on a request description, do you get a ``Response[_]`` directly, or is it wrapped in a ``Future`` or a ``Task``?
* ``S``, the type of supported streams. If ``Nothing``, streaming is not supported. Otherwise, the given type can be used to send request bodies or receive response bodies.

Below is a summary of all the backends. See the sections on individual backend implementations for more information.

================================ ============================ ================================================
Class                            Response wrapper             Supported stream type
================================ ============================ ================================================
``HttpURLConnectionBackend``     None (``Id``)                n/a 
``TryBackend``                   ``scala.util.Try``                      n/a
``AkkaHttpBackend``              ``scala.concurrent.Future``  ``akka.stream.scaladsl.Source[ByteString, Any]``
``AsyncHttpClientFutureBackend`` ``scala.concurrent.Future``  n/a
``AsyncHttpClientScalazBackend`` ``scalaz.concurrent.Task``   n/a
``AsyncHttpClientMonixBackend``  ``monix.eval.Task``          ``monix.reactive.Observable[ByteBuffer]``
``AsyncHttpClientCatsBackend``   ``F[_]: cats.effect.Async``  n/a
``AsyncHttpClientFs2Backend``    ``F[_]: cats.effect.Async``  ``fs2.Stream[F, ByteBuffer]``
``OkHttpSyncBackend``            None (``Id``)                n/a
``OkHttpFutureBackend``          ``scala.concurrent.Future``  n/a
``OkHttpMonixBackend``           ``monix.eval.Task``          ``monix.reactive.Observable[ByteBuffer]``
================================ ============================ ================================================

