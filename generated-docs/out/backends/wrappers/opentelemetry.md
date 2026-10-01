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
"com.softwaremill.sttp.client4" %% "opentelemetry-backend" % "4.0.27"
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

## Tracing 

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client4" %% "opentelemetry-backend" % "4.0.27"
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

```scala
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
"com.softwaremill.sttp.client4" %% "opentelemetry-tracing-zio-backend" % "4.0.27"  // for ZIO 2.x
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

## Metrics (cats-effect, otel4s)

Add the following dependency to your project:
```scala
"com.softwaremill.sttp.client4" %% "opentelemetry-otel4s-metrics-backend" % "4.0.27"
```

This backend depends on [otel4s](https://github.com/typelevel/otel4s).

Use `Otel4sMetricsBackend` to enable tracing of a client:
```scala
import cats.effect.*
import org.typelevel.otel4s.metrics.MeterProvider
import sttp.client4.*
import sttp.client4.opentelemetry.otel4s.*

implicit val meterProvider: MeterProvider[IO] = ??? 
val catsBackend: Backend[IO] = ???

Otel4sMetricsBackend(catsBackend, Otel4sMetricsConfig.default)
  .use { backend => ??? }
```

The backend follows the OpenTelemetry [specification](https://opentelemetry.io/docs/specs/semconv/http/http-metrics/)
of HTTP metrics.
The following metrics are available by default:
- [http.client.request.duration](https://opentelemetry.io/docs/specs/semconv/http/http-metrics/#metric-httpclientrequestduration) 
- [http.client.request.body.size](https://opentelemetry.io/docs/specs/semconv/http/http-metrics/#metric-httpclientrequestbodysize) 
- [http.client.response.body.size](https://opentelemetry.io/docs/specs/semconv/http/http-metrics/#metric-httpclientresponsebodysize)
- [http.client.active_requests](https://opentelemetry.io/docs/specs/semconv/http/http-metrics/#metric-httpclientactive_requests)

You can customize histogram buckets, the URL template behavior and the attributes attached to the recorded measurements by providing a custom `Otel4sMetricsConfig`.

### URL template

The `url.template` [experimental attribute](https://opentelemetry.io/docs/specs/semconv/attributes-registry/url/) is not added by default, as URL structures vary widely across APIs. To enable it, provide a `GenericRequest[_, _] => Option[String]` function via the `urlTemplate` config field. Because the function receives the full request, you can use request attributes to pass the template from the call site.

A built-in implementation is available in `UrlTemplates.replaceIds`: it replaces UUIDs and numeric IDs in path segments and query values with `{id}`, and always returns `Some` (the URL unchanged when no IDs are found).

```scala
import cats.effect.*
import org.typelevel.otel4s.metrics.MeterProvider
import sttp.client4.*
import sttp.client4.opentelemetry.otel4s.*

implicit val meterProvider: MeterProvider[IO] = ???
val catsBackend: Backend[IO] = ???

// Use the built-in implementation (replaces UUIDs and numeric IDs with {id}):
Otel4sMetricsBackend(
  catsBackend,
  Otel4sMetricsConfig(
    requestDurationHistogramBuckets = Otel4sMetricsConfig.DefaultDurationBuckets,
    requestBodySizeHistogramBuckets = None,
    responseBodySizeHistogramBuckets = None,
    urlTemplate = UrlTemplates.replaceIds
  )
)

// Or provide a custom function based on request attributes:
import sttp.attributes.AttributeKey
val UrlTemplateKey = new AttributeKey[String]("UrlTemplateKey")
Otel4sMetricsBackend(
  catsBackend,
  Otel4sMetricsConfig(
    requestDurationHistogramBuckets = Otel4sMetricsConfig.DefaultDurationBuckets,
    requestBodySizeHistogramBuckets = None,
    responseBodySizeHistogramBuckets = None,
    urlTemplate = req => req.attribute(UrlTemplateKey)
  )
)
// Then, at the call site:
// basicRequest.get(uri"...").attribute(UrlTemplateKey, "/users/{id}")
```

### Custom attributes

Apart from the attributes defined by the semantic conventions, you can attach arbitrary attributes to the recorded
measurements, e.g. to label them with a business dimension. Provide a `GenericRequest[_, _] => Attributes` function via
the `extraAttributes` config field; the returned attributes are added to all four metrics. Because the function receives
the full request, the attributes can either be derived from it, or passed from the call site using a request attribute.

```scala
import cats.effect.*
import org.typelevel.otel4s.{Attribute, Attributes}
import org.typelevel.otel4s.metrics.MeterProvider
import sttp.attributes.AttributeKey
import sttp.client4.*
import sttp.client4.opentelemetry.otel4s.*

implicit val meterProvider: MeterProvider[IO] = ???
val catsBackend: Backend[IO] = ???

val FlowKey = new AttributeKey[String]("FlowKey")

Otel4sMetricsBackend(
  catsBackend,
  Otel4sMetricsConfig(
    requestDurationHistogramBuckets = Otel4sMetricsConfig.DefaultDurationBuckets,
    requestBodySizeHistogramBuckets = None,
    responseBodySizeHistogramBuckets = None,
    extraAttributes = req => Attributes(Attribute("flow", req.attribute(FlowKey).getOrElse("unknown")))
  )
)
// Then, at the call site:
// basicRequest.get(uri"...").attribute(FlowKey, "checkout")
```

Each distinct combination of attribute values creates a separate time series, so only low-cardinality values should be
used; for the same reason, prefer returning the same attribute keys for all requests sent using a given backend.
Extra attributes cannot override the semantic convention attributes set by the backend (such as `http.request.method`).
Some of those are only set in certain cases (e.g. `error.type` only for failed requests), and otherwise an extra
attribute with the same key is recorded as-is. Hence, avoid using semantic convention keys for extra attributes.

## Tracing (cats-effect, otel4s)

Add the following dependency to your project:
```scala
"com.softwaremill.sttp.client4" %% "opentelemetry-otel4s-tracing-backend" % "4.0.27"
```

This backend depends on [otel4s](https://github.com/typelevel/otel4s).

Use `Otel4sTracingBackend` to enable tracing of a client:
```scala
import cats.effect.*
import org.typelevel.otel4s.trace.TracerProvider
import sttp.client4.*
import sttp.client4.opentelemetry.otel4s.*

implicit val tracerProvider: TracerProvider[IO] = ???
val catsBackend: Backend[IO] = ???

Otel4sTracingBackend(catsBackend, Otel4sTracingConfig.default)
```

The backend follows the OpenTelemetry [specification](https://opentelemetry.io/docs/specs/semconv/http/http-spans/) 
of HTTP spans.

You can customize span name and attached attributes by providing a custom `Otel4sTracingConfig`.

## Tracing (cats-effect, trace4cats)

The [trace4cats](https://github.com/trace4cats/trace4cats) project includes sttp-client integration.
