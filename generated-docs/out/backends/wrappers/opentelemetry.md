# Opentelemetry

Both backends from below depend only on [opentelemetry](https://github.com/open-telemetry/opentelemetry-java).
The opentelemetry are type of wrapper backends, so they wrap any other backend. They require an instance of opentelemetry.

To use, any of below backends, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "opentelemetry" % "3.6.1"
```

### Opentelemetry tracing backend

To obtain instance of OpenTelemetryTracingBackend:

```scala
OpenTelemetryTracingBackend(
  sttpBackend,
  openTelemetry
)
```

By default, the span is named after the HTTP method (e.g "HTTP POST") as [recommended by OpenTelemetry](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md#name) for HTTP clients. 
It can be customized by setting the third argument in constructor: 
```scala
OpenTelemetryTracingBackend(
  sttpBackend,
  openTelemetry,
  request => s"HTTP ${request.method.method}"
)
```

There is also the possibility to customize tracer name and version by using constructor parameter:
```scala
OpenTelemetryTracingBackend(
  sttpBackend,
  openTelemetry,
  tracerConfig = Some(TracerConfig("my_custom_tracer_name", "1.0.0"))
)
```

### Opentelemetry metrics backend

To obtain instance of OpenTelemetryMetricsBackend:

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

There is also the possibility to customize meter name and version by using constructor parameter:
```scala
OpenTelemetryMetricsBackend(
  sttpBackend,
  openTelemetry,
  meterConfig = Some(MeterConfig("my_custom_meter_name", "1.0.0"))
)
```