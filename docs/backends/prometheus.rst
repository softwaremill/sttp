.. _prometheus_backend:

Prometheus backend
=============

To use, add the following dependency to your project::

  "com.softwaremill.sttp" %% "prometheus-backend" % "1.1.6"

This backend depends on `Prometheus JVM Client <https://github.com/prometheus/client_java>`_. Keep in mind this backend registers histograms and gathers request times, but you have to expose those metrics to `Prometheus <https://prometheus.io/>`_ e.g. using  `prometheus-akka-http <https://github.com/lonelyplanet/prometheus-akka-http>`_.

The Prometheus backend wraps any other backend, for example::

  implicit val sttpBackend = PrometheusBackend(AkkaHttpBackend())

It uses by default ``sttp_request_latency`` histogram name, defined in ``PrometheusBackend.DefaultHistogramName``. It is possible to define custom histograms name by passing function mapping request to histogram name::

  implicit val sttpBackend = PrometheusBackend(AkkaHttpBackend(), Some(request => request.uri.toString))