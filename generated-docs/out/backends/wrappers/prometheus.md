# Prometheus backend

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "prometheus-backend" % "3.3.10"
```

and some imports:

```scala
import sttp.client3.prometheus._
```

This backend depends on [Prometheus JVM Client](https://github.com/prometheus/client_java). Keep in mind this backend registers histograms and gathers request times, but you have to expose those metrics to [Prometheus](https://prometheus.io/) e.g. using [prometheus-akka-http](https://github.com/lonelyplanet/prometheus-akka-http).

The Prometheus backend wraps any other backend, for example:

```scala
import sttp.client3.akkahttp._
val backend = PrometheusBackend(AkkaHttpBackend())
```

It gathers request execution times in `Histogram`. It uses by default `sttp_request_latency` name, defined in `PrometheusBackend.DefaultHistogramName`. It is possible to define custom histograms name by passing function mapping request to histogram name:

```scala
import sttp.client3.akkahttp._
val backend = PrometheusBackend(
  AkkaHttpBackend(), 
  requestToHistogramNameMapper = request => Some(HistogramCollectorConfig(request.uri.host.getOrElse("example.com")))
)
```

You can disable request histograms by passing `None` returning function:

```scala
import sttp.client3.akkahttp._
val backend = PrometheusBackend(AkkaHttpBackend(), requestToHistogramNameMapper = _ => None)
```

This backend also offers `Gauge` with currently in-progress requests number. It uses by default `sttp_requests_in_progress` name, defined in `PrometheusBackend.DefaultRequestsInProgressGaugeName`. It is possible to define custom gauge name by passing function mapping request to gauge name:

```scala
import sttp.client3.akkahttp._
val backend = PrometheusBackend(
  AkkaHttpBackend(), 
  requestToInProgressGaugeNameMapper = request => Some(CollectorConfig(request.uri.host.getOrElse("example.com")))
)
```

You can disable request in-progress gauges by passing `None` returning function:

```scala
import sttp.client3.akkahttp._
val backend = PrometheusBackend(AkkaHttpBackend(), requestToInProgressGaugeNameMapper = _ => None)
```
