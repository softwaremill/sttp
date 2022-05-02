# Opentelemetry metrics backend

To use, add the following dependency to your project:


```
"com.softwaremill.sttp.client3" %% "opentelemetry-metrics-backend" % "@VERSION@"
```

This backend depends only on [opentelemetry](https://github.com/open-telemetry/opentelemetry-java).

The opentelemetry backend wraps any other backend, but it's useless without an instance of opentelemetry. To obtain instance of OpenTelemetryMetricsBackend:

```scala
OpenTelemetryMetricsBackend(
  sttpBackend,
  openTelemetry
)
```

All counters have provided default names, but the names can be customized by setting correct parameters in constructor:
```scala
OpenTelemetryMetricsBackend(
  sttpBackend,
  openTelemetry,
  responseToSuccessCounterMapper = _ => Some(CollectorConfig("my_custom_counter_name"))
)
```