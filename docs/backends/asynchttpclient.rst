async-http-client backend
=========================

To use, add the following dependency to your project::

  "com.softwaremill.sttp" %% "async-http-client-backend-future" % "1.1.11"
  // or
  "com.softwaremill.sttp" %% "async-http-client-backend-scalaz" % "1.1.11"
  // or
  "com.softwaremill.sttp" %% "async-http-client-backend-monix" % "1.1.11"
  // or
  "com.softwaremill.sttp" %% "async-http-client-backend-cats" % "1.1.11"

This backend depends on `async-http-client <https://github.com/AsyncHttpClient/async-http-client>`_.
A fully **asynchronous** backend, which uses `Netty <http://netty.io>`_ behind the
scenes. 

The responses are wrapped depending on the dependency chosen in either a:

* standard Scala ``Future``
* `Scalaz <https://github.com/scalaz/scalaz>`_ ``Task``. There's a transitive dependency on ``scalaz-concurrent``.
* `Monix <https://monix.io>`_ ``Task``. There's a transitive dependency on ``monix-eval``.
* Any type implementing the `Cats Effect <https://github.com/typelevel/cats-effect>`_ ``Async`` typeclass, such as ``cats.effect.IO``. There's a transitive dependency on ``cats-effect``.

Next you'll need to add an implicit value::

  implicit val sttpBackend = AsyncHttpClientFutureBackend()
  
  // or, if you're using the scalaz version:
  implicit val sttpBackend = AsyncHttpClientScalazBackend()
  
  // or, if you're using the monix version:
  implicit val sttpBackend = AsyncHttpClientMonixBackend()
  
  // or, if you're using the cats effect version:
  implicit val sttpBackend = AsyncHttpClientCatsBackend[cats.effect.IO]()
  
  // or, if you'd like to use custom configuration:
  implicit val sttpBackend = AsyncHttpClientFutureBackend.usingConfig(asyncHttpClientConfig)
  
  // or, if you'd like to instantiate the AsyncHttpClient yourself:
  implicit val sttpBackend = AsyncHttpClientFutureBackend.usingClient(asyncHttpClient)

Streaming using Monix
---------------------

The Monix backend supports streaming (as both Monix and Async Http Client support reactive streams ``Publisher`` s out of the box). The type of supported streams in this case is ``Observable[ByteBuffer]``. That is, you can set such an observable as a request body::

  import com.softwaremill.sttp._
  
  import java.nio.ByteBuffer
  import monix.reactive.Observable
  
  val obs: Observable[ByteBuffer] =  ...
  
  sttp
    .streamBody(obs)
    .post(uri"...")

And receive responses as an observable stream::

  import com.softwaremill.sttp._
  import com.softwaremill.sttp.asynchttpclient.monix._
  
  import java.nio.ByteBuffer
  import monix.eval.Task
  import monix.reactive.Observable
  
  implicit val sttpBackend = AsyncHttpClientMonixBackend()
  
  val response: Task[Response[Observable[ByteBuffer]]] = 
    sttp
      .post(uri"...")
      .response(asStream[Observable[ByteBuffer]])
      .send()

It's also possible to use `fs2 <https://github.com/functional-streams-for-scala/fs2>`_ streams for sending request & receiving responses.

