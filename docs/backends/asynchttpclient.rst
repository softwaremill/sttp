async-http-client backend
=========================

To use, add the following dependency to your project::

  "com.softwaremill.sttp" %% "async-http-client-backend-future" % "1.5.12"
  // or
  "com.softwaremill.sttp" %% "async-http-client-backend-scalaz" % "1.5.12"
  // or
  "com.softwaremill.sttp" %% "async-http-client-backend-zio" % "1.5.12"
  // or
  "com.softwaremill.sttp" %% "async-http-client-backend-monix" % "1.5.12"
  // or
  "com.softwaremill.sttp" %% "async-http-client-backend-cats" % "1.5.12"
  // or
  "com.softwaremill.sttp" %% "async-http-client-backend-fs2" % "1.5.12"

This backend depends on `async-http-client <https://github.com/AsyncHttpClient/async-http-client>`_.
A fully **asynchronous** backend, which uses `Netty <http://netty.io>`_ behind the
scenes. 

The responses are wrapped depending on the dependency chosen in either a:

* standard Scala ``Future``
* `Scalaz <https://github.com/scalaz/scalaz>`_ ``Task``. There's a transitive dependency on ``scalaz-concurrent``.
* `ZIO <https://github.com/scalaz/scalaz-zio>`_ ``IO``. There's a transitive dependency on ``scalaz-zio``.
* `Monix <https://monix.io>`_ ``Task``. There's a transitive dependency on ``monix-eval``.
* Any type implementing the `Cats Effect <https://github.com/typelevel/cats-effect>`_ ``Async`` typeclass, such as ``cats.effect.IO``. There's a transitive dependency on ``cats-effect``.
* `fs2 <https://github.com/functional-streams-for-scala/fs2>`_ ``Stream``. There are transitive dependencies on ``fs2``, ``fs2-reactive-streams``, and ``cats-effect``.

Next you'll need to add an implicit value::

  implicit val sttpBackend = AsyncHttpClientFutureBackend()
  
  // or, if you're using the scalaz version:
  implicit val sttpBackend = AsyncHttpClientScalazBackend()

  // or, if you're using the zio version:
  implicit val sttpBackend = AsyncHttpClientZioBackend()
  
  // or, if you're using the monix version:
  implicit val sttpBackend = AsyncHttpClientMonixBackend()
  
  // or, if you're using the cats effect version:
  implicit val sttpBackend = AsyncHttpClientCatsBackend[cats.effect.IO]()

  // or, if you're using the fs2 version:
  implicit val sttpBackend = AsyncHttpClientFs2Backend[cats.effect.IO]()
  
  // or, if you'd like to use custom configuration:
  implicit val sttpBackend = AsyncHttpClientFutureBackend.usingConfig(asyncHttpClientConfig)
  
  // or, if you'd like to use adjust the configuration sttp creates:
  implicit val sttpBackend = AsyncHttpClientFutureBackend.usingConfigBuilder(adjustFunction, sttpOptions)
  
  // or, if you'd like to instantiate the AsyncHttpClient yourself:
  implicit val sttpBackend = AsyncHttpClientFutureBackend.usingClient(asyncHttpClient)

Streaming using Monix
---------------------

The Monix backend supports streaming (as both Monix and Async Http Client support reactive streams ``Publisher`` s out of the box). The type of supported streams in this case is ``Observable[ByteBuffer]``. That is, you can set such an observable as a request body::

  import com.softwaremill.sttp._
  import com.softwaremill.sttp.asynchttpclient.monix._
  
  import java.nio.ByteBuffer
  import monix.reactive.Observable
  
  implicit val sttpBackend = AsyncHttpClientMonixBackend()

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
  import scala.concurrent.duration.Duration

  implicit val sttpBackend = AsyncHttpClientMonixBackend()
  
  val response: Task[Response[Observable[ByteBuffer]]] = 
    sttp
      .post(uri"...")
      .response(asStream[Observable[ByteBuffer]])
      .readTimeout(Duration.Inf)
      .send()

Streaming using fs2
-------------------

The fs2 backend supports streaming in any instance of the ``cats.effect.Effect`` typeclass, such as ``cats.effect.IO``. If ``IO`` is used then the type of supported streams is ``fs2.Stream[IO, ByteBuffer]``.

Requests can be sent with a streaming body like this::

  import com.softwaremill.sttp._
  import com.softwaremill.sttp.asynchttpclient.fs2.AsyncHttpClientFs2Backend

  import java.nio.ByteBuffer
  import cats.effect.{ContextShift, IO}
  import fs2.Stream

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)
  implicit val sttpBackend = AsyncHttpClientFs2Backend[IO]()

  val stream: Stream[IO, ByteBuffer] = ...

  sttp
    .streamBody(stream)
    .post(uri"...")

Responses can also be streamed::

  import com.softwaremill.sttp._
  import com.softwaremill.sttp.asynchttpclient.fs2.AsyncHttpClientFs2Backend

  import java.nio.ByteBuffer
  import cats.effect.{ContextShift, IO}
  import fs2.Stream
  import scala.concurrent.duration.Duration

  implicit val cs: ContextShift[IO] = IO.contextShift(ExecutionContext.Implicits.global)
  implicit val sttpBackend = AsyncHttpClientFs2Backend[IO]()

  val response: IO[Response[Stream[IO, ByteBuffer]]] =
    sttp
      .post(uri"...")
      .response(asStream[Stream[IO, ByteBuffer]])
      .readTimeout(Duration.Inf)
      .send()
