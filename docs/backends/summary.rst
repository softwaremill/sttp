Supported backends
==================

================================ ============================ ================================================
Class                            Result wrapper               Supported stream type
================================ ============================ ================================================
``HttpURLConnectionBackend``     None (``Id``)                n/a 
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

