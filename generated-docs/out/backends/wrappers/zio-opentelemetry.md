# zio-telemetry opentelemetry backend 

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "zio-telemetry-opentelemetry-backend" % "3.3.0-RC4"
```

This backend depends on [zio-opentelemetry](https://github.com/zio/zio-telemetry).

The opentelemetry backend wraps a `Task` based ZIO backend and yields a backend of type `SttpBackend[RIO[Tracing, *], Nothing, WS_HANDLER]`. The yielded effects are of type `RIO[Tracing, *]` which mean they can be a child of a other span created in your ZIO program.

Here's how you construct `ZioTelemetryOpenTelemetryBackend`. I would recommend wrapping this is in `ZLayer`

```scala
new ZioTelemetryOpenTelemetryBackend(zioBackend)
```

Additionally you can add tags per request by supplying a `ZioTelemetryOpenTelemetryTracer`

```scala
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes
import sttp.client3._
import zio._
import zio.telemetry.opentelemetry._
import sttp.client3.ziotelemetry.opentelemetry._

implicit val zioBackend: SttpBackend[Task, Any] = ???

def sttpTracer: ZioTelemetryOpenTelemetryTracer = new ZioTelemetryOpenTelemetryTracer {
    def before[T](request: Request[T, Nothing]): RIO[Tracing, Unit] =
      Tracing.setAttribute(SemanticAttributes.HTTP_METHOD.getKey, request.method.method) *>
      Tracing.setAttribute(SemanticAttributes.HTTP_URL.getKey, request.uri.toString()) *>
      ZIO.unit
    
    def after[T](response: Response[T]): RIO[Tracing, Unit] =
      Tracing.setAttribute(SemanticAttributes.HTTP_STATUS_CODE.getKey, response.code.code) *>
      ZIO.unit
}

ZioTelemetryOpenTelemetryBackend(zioBackend, sttpTracer)
```


