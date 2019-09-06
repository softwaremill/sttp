.. _prometheus_backend:

Prometheus backend
==================

To use, add the following dependency to your project::

  "com.softwaremill.sttp" %% "prometheus-backend" % "1.6.6"

This backend depends on `Prometheus JVM Client <https://github.com/prometheus/client_java>`_. Keep in mind this backend registers histograms and gathers request times, but you have to expose those metrics to `Prometheus <https://prometheus.io/>`_ e.g. using  `prometheus-akka-http <https://github.com/lonelyplanet/prometheus-akka-http>`_.

The Prometheus backend wraps any other backend, for example::

  implicit val sttpBackend = PrometheusBackend(AkkaHttpBackend())

It gathers request execution times in ``Histogram``. It uses by default ``sttp_request_latency`` name, defined in ``PrometheusBackend.DefaultHistogramName``. It is possible to define custom histograms name by passing function mapping request to histogram name::

  implicit val sttpBackend = PrometheusBackend(AkkaHttpBackend(), request => Some(request.uri.host))

You can disable request histograms by passing ``None`` returning function::

  implicit val sttpBackend = PrometheusBackend(AkkaHttpBackend(), _ => None)

This backend also offers ``Gauge`` with currently in-progress requests number. It uses by default ``sttp_requests_in_progress`` name, defined in ``PrometheusBackend.DefaultRequestsInProgressGaugeName``. It is possible to define custom gauge name by passing function mapping request to gauge name::

   implicit val sttpBackend = PrometheusBackend(AkkaHttpBackend(), requestToInProgressGaugeNameMapper = request => Some(request.uri.host))

You can disable request in-progress gauges by passing ``None`` returning function::

  implicit val sttpBackend = PrometheusBackend(AkkaHttpBackend(), requestToInProgressGaugeNameMapper = _ => None)
