# Prometheus backend

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client4" %% "prometheus-backend" % "4.0.6"
```

and some imports:

```scala
import sttp.client4.prometheus.*
```

This backend depends on [Prometheus JVM Client](https://github.com/prometheus/client_java). Keep in mind this backend registers histograms and gathers request times, but you have to expose those metrics to [Prometheus](https://prometheus.io/).

The Prometheus backend wraps any other backend, for example:

```scala
import sttp.client4.pekkohttp.*
val backend = PrometheusBackend(PekkoHttpBackend())
```

It gathers request execution times in `Histogram`. It uses by default `http_client_request_duration_seconds` name, defined in `PrometheusBackend.DefaultHistogramName`. It is possible to define custom histograms name by passing function mapping request to histogram name:

```scala
import sttp.client4.pekkohttp.*
val backend = PrometheusBackend(
  PekkoHttpBackend(),
  PrometheusConfig(
    requestToHistogramNameMapper = request => Some(HistogramCollectorConfig(request.uri.host.getOrElse("example.com")))
  )
)
```

You can disable request histograms by passing `None` returning function:

```scala
import sttp.client4.pekkohttp.*
val backend = PrometheusBackend(PekkoHttpBackend(), PrometheusConfig(requestToHistogramNameMapper = _ => None))
```

This backend also offers `Gauge` with currently in-progress requests number. It uses by default `http_client_requests_active` name, defined in `PrometheusBackend.DefaultRequestsActiveCounterName`. It is possible to define custom gauge name by passing function mapping request to gauge name:

```scala
import sttp.client4.pekkohttp.*
val backend = PrometheusBackend(
  PekkoHttpBackend(),
  PrometheusConfig(
    requestToInProgressGaugeNameMapper = request => Some(CollectorConfig(request.uri.host.getOrElse("example.com")))
  )
)
```

You can disable request in-progress gauges by passing `None` returning function:

```scala
import sttp.client4.pekkohttp.*
val backend = PrometheusBackend(PekkoHttpBackend(), PrometheusConfig(requestToInProgressGaugeNameMapper = _ => None))
```
