# zio-telemetry opentelemetry backend 

To use, add the following dependency to your project (the `zio-*` modules depend on ZIO 2.x; for ZIO 1.x support, use `zio1-*`):

```
"com.softwaremill.sttp.client3" %% "zio-telemetry-opentelemetry-backend" % "@VERSION@"  // for ZIO 2.x
"com.softwaremill.sttp.client3" %% "zio1-telemetry-opentelemetry-backend" % "@VERSION@" // for ZIO 1.x
```

This backend depends on [zio-opentelemetry](https://github.com/zio/zio-telemetry).

The opentelemetry backend wraps a `Task` based ZIO backend.
In order to do that, you need to provide the wrapper with a `Tracing.Service` from zio-telemetry.

Here's how you construct `ZioTelemetryOpenTelemetryBackend`. I would recommend wrapping this is in `ZLayer`

```scala
ZioTelemetryOpenTelemetryBackend(
  sttpBackend,
  tracing
)
```

By default, the span is named after the HTTP method (e.g "HTTP POST") as [recommended by OpenTelemetry](https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/http.md#name) for HTTP clients.
You can override the default span name or add additional tags per request by supplying a `ZioTelemetryOpenTelemetryTracer`.

```scala mdoc:compile-only
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import sttp.client3._
import zio._
import zio.telemetry.opentelemetry._
import sttp.client3.ziotelemetry.opentelemetry._

val zioBackend: SttpBackend[Task, Any] = ???
val tracing: Tracing.Service = ???

def sttpTracer: ZioTelemetryOpenTelemetryTracer = new ZioTelemetryOpenTelemetryTracer {
    override def spanName[T](request: Request[T, Nothing]): String = ???

    def before[T](request: Request[T, Nothing]): RIO[Tracing.Service, Unit] =
      Tracing.setAttribute(SemanticAttributes.HTTP_METHOD.getKey, request.method.method) *>
      Tracing.setAttribute(SemanticAttributes.HTTP_URL.getKey, request.uri.toString()) *>
      ZIO.unit
    
    def after[T](response: Response[T]): RIO[Tracing.Service, Unit] =
      Tracing.setAttribute(SemanticAttributes.HTTP_STATUS_CODE.getKey, response.code.code) *>
      ZIO.unit
}

ZioTelemetryOpenTelemetryBackend(zioBackend, tracing, sttpTracer)
```


