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

Example backend wrapper
-----------------------

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

  
