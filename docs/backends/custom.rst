.. _custombackends:

Custom backends, logging, metrics
=================================

It is also entirely possible to write custom backends (if doing so, please consider contributing!) or wrap an existing one. One can even write completely generic wrappers for any delegate backend, as each backend comes equipped with a monad for the response type. This brings the possibility to ``map`` and ``flatMap`` over responses.

Possible use-cases for wrapper-backend include:

* logging
* capturing metrics
* request signing (transforming the request before sending it to the delegate)

Request tagging
---------------

Each request contains a ``tags: Map[String, Any]`` map. This map can be used to tag the request with any backend-specific information, and isn't used in any way by sttp itself.

Tags can be added to a request using the ``def tag(k: String, v: Any)`` method, and read using the ``def tag(k: String): Option[Any]`` method.

Backends, or backend wrappers can use tags e.g. for logging, passing a metric name, using different connection pools, or even different delegate backends.

Backend wrappers and redirects
------------------------------

By default redirects are handled at a low level, using a wrapper around the main, concrete backend: each of the backend factory methods, e.g. ``HttpURLConnectionBackend()`` returns a backend wrapped in ``FollowRedirectsBackend``.

This causes any further backend wrappers to handle a request which involves redirects as one whole, without the intermediate requests. However, wrappers which collects metrics, implements tracing or handles request retries might want to handle every request in the redirect chain. This can be achieved by layering another ``FollowRedirectsBackend`` on top of the wrapper. Only the top-level follow redirects backend will handle redirects, other follow redirect wrappers (at lower levels) will be disabled.

For example::

  class MyWrapper[R[_], S] private (delegate: SttpBackend[R, S])
    extends SttpBackend[R, S] {

    ...
  }

  object MyWrapper {
    def apply[R[_], S](delegate: SttpBackend[R, S]): SttpBackend[R, S] = {
      // disables any other FollowRedirectsBackend-s further down the delegate chain
      new FollowRedirectsBackend(new MyWrapper(delegate))
    }
  }

Example logging backend wrapper
-------------------------------

Often it's useful to setup system-wide logging for failed requests. This is possible using a backend wrapper. In this example, we are using ``scala-logging`` for the logging itself, but of course any logging library can be used::

  import com.softwaremill.sttp.{MonadError, Request, Response, SttpBackend}
  import com.typesafe.scalalogging.StrictLogging

  class LoggingSttpBackend[R[_], S](delegate: SttpBackend[R, S]) extends SttpBackend[R, S]
    with StrictLogging {

    override def send[T](request: Request[T, S]): R[Response[T]] = {
      responseMonad.map(responseMonad.handleError(delegate.send(request)) {
        case e: Exception =>
          logger.error(s"Exception when sending request: $request", e)
          responseMonad.error(e)
      }) { response =>
        if (response.isSuccess) {
          logger.debug(s"For request: $request got response: $response")
        } else {
          logger.warn(s"For request: $request got response: $response")
        }
        response
      }
    }
    override def close(): Unit = delegate.close()
    override def responseMonad: MonadError[R] = delegate.responseMonad
  }


Note that there are three possible outcomes of a request:

* an exception is thrown (handled with ``responseMonad.handleError``), e.g. because of a connection error; here, this is logged with level ``ERROR``.
* the response completes normally, but the server returns a non-2xx response code. Here, this case is logged with level ``WARN``.
* the response completes normally with 2xx response code. Here, this case is logged with level ``DEBUG``.

It's quite easy to customize this backend to your particular needs - just copy the code!

Example metrics backend wrapper
-------------------------------

Below is an example on how to implement a backend wrapper, which sends metrics for completed requests and wraps any ``Future``-based backend::

  // the metrics infrastructure
  trait MetricsServer {
    def reportDuration(name: String, duration: Long): Unit
  }

  class CloudMetricsServer extends MetricsServer {
    override def reportDuration(name: String, duration: Long): Unit = ???
  }

  // the backend wrapper
  class MetricWrapper[S](delegate: SttpBackend[Future, S],
                            metrics: MetricsServer)
      extends SttpBackend[Future, S] {

    override def send[T](request: Request[T, S]): Future[Response[T]] = {
      val start = System.currentTimeMillis()

      def report(metricSuffix: String): Unit = {
        val metricPrefix = request.tag("metric").getOrElse("?")
        val end = System.currentTimeMillis()
        metrics.reportDuration(metricPrefix + "-" + metricSuffix, end - start)
      }

      delegate.send(request).andThen {
        case Success(response) if response.is200 => report("ok")
        case Success(response)                   => report("notok")
        case Failure(t)                          => report("exception")
      }
    }

    override def close(): Unit = delegate.close()

    override def responseMonad: MonadError[Future] = delegate.responseMonad
  }

  // example usage
  implicit val backend = new MetricWrapper(
    AkkaHttpBackend(),
    new CloudMetricsServer()
  )

  sttp
    .get(uri"http://company.com/api/service1")
    .tag("metric", "service1")
    .send()

Example retrying backend wrapper
--------------------------------

Handling retries is a complex problem when it comes to HTTP requests. When is a request retryable? There are a couple of things to take into account:

* connection exceptions are generally good candidates for retries
* only idempotent HTTP methods (such as ``GET``) could potentially be retried
* some HTTP status codes might also be retryable (e.g. ``500 Internal Server Error`` or ``503 Service Unavailable``)

In some cases it's possible to implement a generic retry mechanism; such a mechanism should take into account logging, metrics, limiting the number of retries and a backoff mechanism. These mechanisms could be quite simple, or involve e.g. retry budgets (see `Finagle's <https://twitter.github.io/finagle/guide/Clients.html#retries>`_ documentation on retries). In sttp, it's possible to recover from errors using the ``responseMonad``. A starting point for a retrying backend could be::

  import com.softwaremill.sttp.{MonadError, Request, Response, SttpBackend}

  class RetryingBackend[R[_], S](
      delegate: SttpBackend[R, S],
      shouldRetry: (Request[_, _], Either[Throwable, Response[_]]) => Boolean,
      maxRetries: Int)
      extends SttpBackend[R, S] {

    override def send[T](request: Request[T, S]): R[Response[T]] = {
      sendWithRetryCounter(request, 0)
    }

    private def sendWithRetryCounter[T](request: Request[T, S],
                                        retries: Int): R[Response[T]] = {
      val r = responseMonad.handleError(delegate.send(request)) {
        case t if shouldRetry(request, Left(t)) && retries < maxRetries =>
          sendWithRetryCounter(request, retries + 1)
      }

      responseMonad.flatMap(r) { resp =>
        if (shouldRetry(request, Right(resp)) && retries < maxRetries) {
          sendWithRetryCounter(request, retries + 1)
        } else {
          responseMonad.unit(resp)
        }
      }
    }

    override def close(): Unit = delegate.close()

    override def responseMonad: MonadError[R] = delegate.responseMonad
  }

Note that some backends also have built-in retry mechanisms, e.g. `akka-http <https://doc.akka.io/docs/akka-http/current/scala/http/client-side/host-level.html#retrying-a-request>`_ or `OkHttp <http://square.github.io/okhttp>`_ (see the builder's ``retryOnConnectionFailure`` method).

Example new backend
--------------------------------

Implementing a new backend is made easy as the tests are published in the ``core`` jar file under the ``tests`` classifier. Simply add the follow dependencies to your ``build.sbt``::

  "com.softwaremill.sttp" %% "core" % "1.5.12" % "test" classifier "tests",
  "com.typesafe.akka" %% "akka-http" % "10.1.1" % "test",
  "ch.megard" %% "akka-http-cors" % "0.3.0" % "test",
  "com.typesafe.akka" %% "akka-stream" % "2.5.12" % "test",
  "org.scalatest" %% "scalatest" % "3.0.5" % "test"

Implement your backend and extend the ``HttpTest`` class::

  import com.softwaremill.sttp.SttpBackend
  import com.softwaremill.sttp.testing.{ConvertToFuture, HttpTest}

  class MyCustomBackendHttpTest extends HttpTest[Future] {

    override implicit val convertToFuture: ConvertToFuture[Future] = ConvertToFuture.future
    override implicit lazy val backend: SttpBackend[Future, Nothing] = new MyCustomBackend()

  }

You can find a more detailed example in the `sttp-vertx <https://github.com/guymers/sttp-vertx>`_ repository.
