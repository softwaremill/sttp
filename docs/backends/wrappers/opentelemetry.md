# OpenTelemetry

Currently, the following OpenTelemetry features are supported:

- metrics using `OpenTelemetryMetricsBackend`, wrapping any other backend
- tracing using `OpenTelemetryTracingZioBackend`, wrapping any ZIO2 backend
- tracing using [trace4cats](https://github.com/trace4cats/trace4cats), wrapping a cats-effect backend

### Metrics

The backend depends only on [opentelemetry-api](https://github.com/open-telemetry/opentelemetry-java). To use add the
following dependency to your project:

```
"com.softwaremill.sttp.client4" %% "opentelemetry-metrics-backend" % "@VERSION@"
```

Then an instance can be obtained as follows:

```scala mdoc:compile-only
import scala.concurrent.Future
import sttp.client4._
import sttp.client4.opentelemetry._
import io.opentelemetry.api.OpenTelemetry

// any effect and capabilities are supported
val sttpBackend: Backend[Future] = ???
val openTelemetry: OpenTelemetry = ???

OpenTelemetryMetricsBackend(sttpBackend, openTelemetry)
```

All counters have provided default names, but the names can be customized by setting correct parameters in constructor:

```scala mdoc:compile-only
import scala.concurrent.Future
import sttp.client4._
import sttp.client4.opentelemetry._
import io.opentelemetry.api.OpenTelemetry

val sttpBackend: Backend[Future] = ???
val openTelemetry: OpenTelemetry = ???

OpenTelemetryMetricsBackend(
  sttpBackend,
  OpenTelemetryMetricsConfig(
    openTelemetry,
    responseToSuccessCounterMapper = _ => Some(CollectorConfig("my_custom_counter_name"))
  )
)
```

### Tracing (ZIO)

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client4" %% "opentelemetry-tracing-zio-backend" % "@VERSION@"  // for ZIO 2.x
```

This backend depends on [zio-opentelemetry](https://github.com/zio/zio-telemetry).

The OpenTelemetry backend wraps a `Task` based ZIO backend.
In order to do that, you need to provide the wrapper with a `Tracing` from zio-telemetry.

Here's how you construct `ZioTelemetryOpenTelemetryBackend`. I would recommend wrapping this is in `ZLayer`

```scala mdoc:compile-only
import sttp.client4._
import zio._
import zio.telemetry.opentelemetry._
import sttp.client4.opentelemetry.zio._

val zioBackend: Backend[Task] = ???
val tracing: Tracing = ???

OpenTelemetryTracingZioBackend(zioBackend, tracing)
```

By default, the span is named after the HTTP method (e.g "HTTP POST") as [recommended by OpenTelemetry](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md#name) for HTTP clients,
and the http method, url and response status codes are set as span attributes.
You can override these defaults by supplying a custom `OpenTelemetryZioTracer`.

### Tracing (cats-effect)

The [trace4cats](https://github.com/trace4cats/trace4cats) project includes sttp-client integration.
