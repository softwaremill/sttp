# OpenTelemetry

Currently, the following OpenTelemetry features are supported:

- metrics using `OpenTelemetryMetricsBackend`, wrapping any other backend
- tracing using `OpenTelemetryTracingBackend`, wrapping a synchronous backend
- tracing using `OpenTelemetryTracingZioBackend`, wrapping any ZIO2 backend
- tracing using [trace4cats](https://github.com/trace4cats/trace4cats), wrapping a cats-effect backend

## Metrics

The backend depends only on [opentelemetry-api](https://github.com/open-telemetry/opentelemetry-java). To use add the
following dependency to your project:

```
"com.softwaremill.sttp.client4" %% "opentelemetry-backend" % "@VERSION@"
```

Then an instance can be obtained as follows:

```scala mdoc:compile-only
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

```scala mdoc:compile-only
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

## Tracing 

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client4" %% "opentelemetry-backend" % "@VERSION@"
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
Relies on the built-in OpenTelemetry Java SDK `ContextStorage` mechanism of propagating the tracing context;
by default, this is using `ThreadLocal`s, which works with synchronous/direct-style environments. `Future`s are 
supported through instrumentation provided by the OpenTelemetry javaagent. For functional effect systems, usually 
a dedicated integration library is required.
```

Example usage:

```scala mdoc:compile-only
import sttp.client4.*
import sttp.client4.opentelemetry.*
import io.opentelemetry.api.OpenTelemetry

val sttpBackend: SyncBackend = ???
val openTelemetry: OpenTelemetry = ???

OpenTelemetryTracingBackend(
  sttpBackend,
  OpenTelemetryTracingConfig(
    openTelemetry,
    spanName = request => request.uri.pathSegments.segments.headOption.map(_.v).getOrElse("root")
  )
)
```

## Tracing (ZIO)

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client4" %% "opentelemetry-tracing-zio-backend" % "@VERSION@"  // for ZIO 2.x
```

This backend depends on [zio-opentelemetry](https://github.com/zio/zio-telemetry).

The OpenTelemetry backend wraps a `Task` based ZIO backend.
In order to do that, you need to provide the wrapper with a `Tracing` from zio-telemetry.

Here's how you construct `ZioTelemetryOpenTelemetryBackend`:

```scala mdoc:compile-only
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
