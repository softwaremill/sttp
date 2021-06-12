# zio-telemetry opentelemetry backend 

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "zio-telemetry-opentelemetry-backend" % "@VERSION@"
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

Additionally you can add tags per request by supplying a `ZioTelemetryOpenTelemetryTracer`
(by default, all that happens is that the span for the request is named after using the HTTP method
and path).

```scala mdoc:compile-only
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import sttp.client3._
import zio._
import zio.telemetry.opentelemetry._
import sttp.client3.ziotelemetry.opentelemetry._

val zioBackend: SttpBackend[Task, Any] = ???
val tracing: Tracing.Service = ???

def sttpTracer: ZioTelemetryOpenTelemetryTracer = new ZioTelemetryOpenTelemetryTracer {
    def before[T](request: Request[T, Nothing]): RIO[Tracing, Unit] =
      Tracing.setAttribute(SemanticAttributes.HTTP_METHOD.getKey, request.method.method) *>
      Tracing.setAttribute(SemanticAttributes.HTTP_URL.getKey, request.uri.toString()) *>
      ZIO.unit
    
    def after[T](response: Response[T]): RIO[Tracing, Unit] =
      Tracing.setAttribute(SemanticAttributes.HTTP_STATUS_CODE.getKey, response.code.code) *>
      ZIO.unit
}

ZioTelemetryOpenTelemetryBackend(zioBackend, tracing, sttpTracer)
```


