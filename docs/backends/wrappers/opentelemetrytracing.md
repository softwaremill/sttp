# Opentelemetry tracing backend

To use, add the following dependency to your project:


```
"com.softwaremill.sttp.client3" %% "opentelemetry-tracing-backend" % "@VERSION@"
```

This backend depends only on [opentelemetry](https://github.com/open-telemetry/opentelemetry-java).

The opentelemetry backend wraps any other backend, but it's useless without an instance of opentelemetry. To obtain instance of OpenTelemetryTracingBackend:

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