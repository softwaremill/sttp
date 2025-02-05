# OpenTelemetry

Currently, the following OpenTelemetry features are supported:

- metrics using `OpenTelemetryMetricsBackend`, wrapping any other backend
- tracing using `OpenTelemetryTracingSyncBackend`, wrapping a synchronous backend
- tracing using `OpenTelemetryTracingZioBackend`, wrapping any ZIO2 backend
- tracing using [trace4cats](https://github.com/trace4cats/trace4cats), wrapping a cats-effect backend

## Metrics

The backend depends only on [opentelemetry-api](https://github.com/open-telemetry/opentelemetry-java). To use add the
following dependency to your project:

```
"com.softwaremill.sttp.client4" %% "opentelemetry-backend" % "4.0.0-M26"
```

Then an instance can be obtained as follows:

```scala
import scala.concurrent.Future
import sttp.client4.*
import sttp.client4.opentelemetry.*
import io.opentelemetry.api.OpenTelemetry

// any effect and capabilities are supported
val sttpBackend: Backend[Future] = ???
val openTelemetry: OpenTelemetry = ???

OpenTelemetryMetricsBackend(sttpBackend, openTelemetry)
```

All counters have provided default names, but the names can be customized by setting correct parameters in constructor:

```scala
import scala.concurrent.Future
import sttp.client4.*
import sttp.client4.opentelemetry.*
import io.opentelemetry.api.OpenTelemetry

val sttpBackend: Backend[Future] = ???
val openTelemetry: OpenTelemetry = ???

OpenTelemetryMetricsBackend(
  sttpBackend,
  OpenTelemetryMetricsConfig(
    openTelemetry,
    responseToSuccessCounterMapper = (_, _) => Some(CollectorConfig("my_custom_counter_name"))
  )
)
```

## Tracing (synchronous)

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client4" %% "opentelemetry-backend" % "4.0.0-M26"
```

The backend records traces corresponding to HTTP client calls. The default span name is the HTTP method (e.g. `POST`),
but this can be customized to provide more accurate (but still general) span names by providing a custom 
span-name-generating method (as [recommended by OpenTelemetry](https://opentelemetry.io/docs/specs/semconv/http/http-spans/#name)).
Alternative span naming strategies might include reading request's attributes (to determine the target URI template), 
or parts of the URI.

Other aspects of the backend can be configured as well:

* the `Tracer` instance and context propagators
* how request, response, error attributes are computed

```{note}
The backend relies on context passed using the default mechanism in OpenTelemetry SDK for Java, that is using 
`ThreadLocal`s. This means that the backend will **not** work properly when combined with any asynchronous
execution mechanisms, such as `Future`s.

Moreover, for Java 21+, note that `ThreadLocal`s are not inherited when spawning a new virtual thread.
```

Example usage:

```scala
import sttp.client4.*
import sttp.client4.opentelemetry.*
import io.opentelemetry.api.OpenTelemetry

val sttpBackend: SyncBackend = ???
val openTelemetry: OpenTelemetry = ???

OpenTelemetryTracingSyncBackend(
  sttpBackend,
  OpenTelemetryTracingSyncConfig(
    openTelemetry,
    spanName = request => request.uri.pathSegments.segments.headOption.map(_.v).getOrElse("root")
  )
)
```

## Tracing (ZIO)

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client4" %% "opentelemetry-tracing-zio-backend" % "4.0.0-M26"  // for ZIO 2.x
```

This backend depends on [zio-opentelemetry](https://github.com/zio/zio-telemetry).

The OpenTelemetry backend wraps a `Task` based ZIO backend.
In order to do that, you need to provide the wrapper with a `Tracing` from zio-telemetry.

Here's how you construct `ZioTelemetryOpenTelemetryBackend`:

```scala
import sttp.client4.*
import zio.*
import zio.telemetry.opentelemetry.tracing.*
import sttp.client4.opentelemetry.zio.*

val zioBackend: Backend[Task] = ???
val tracing: Tracing = ???

OpenTelemetryTracingZioBackend(zioBackend, tracing)
```

By default, the span is named after the HTTP method (e.g `POST`) as [recommended by OpenTelemetry](https://opentelemetry.io/docs/specs/semconv/http/http-metrics/#http-client) for HTTP clients, and the http method, url and response status codes are set as span attributes.
You can override these defaults by supplying a custom `OpenTelemetryZioTracer`.

## Tracing (cats-effect)

The [trace4cats](https://github.com/trace4cats/trace4cats) project includes sttp-client integration.
