# zio-telemetry opentracing backend 

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "zio-telemetry-opentracing-backend" % "3.3.10"
```

This backend depends on [zio-opentracing](https://github.com/zio/zio-telemetry).

The opentracing backend wraps a `Task` based ZIO backend and yields a backend of type `SttpBackend[RIO[OpenTracing, *], Nothing, WS_HANDLER]`. The yielded effects are of type `RIO[OpenTracing, *]` which mean they can be a child of a other span created in your ZIO program.

Here's how you construct `ZioTelemetryOpenTracingBackend`. I would recommend wrapping this is in `ZLayer`

```scala
new ZioTelemetryOpenTracingBackend(zioBackend)
```

Additionally you can add tags per request by supplying a `ZioTelemetryOpenTracingTracer`

```scala
import sttp.client3._
import zio._
import zio.telemetry.opentracing._
import sttp.client3.ziotelemetry.opentracing._

implicit val zioBackend: SttpBackend[Task, Any] = ???

def sttpTracer: ZioTelemetryOpenTracingTracer = new ZioTelemetryOpenTracingTracer {
    def before[T](request: Request[T, Nothing]): RIO[OpenTracing, Unit] =
      OpenTracing.tag("span.kind", "client") *>
      OpenTracing.tag("http.method", request.method.method) *>
      OpenTracing.tag("http.url", request.uri.toString()) *>
      OpenTracing.tag("type", "ext") *>
      OpenTracing.tag("subtype", "http")
    
    def after[T](response: Response[T]): RIO[OpenTracing, Unit] =
      OpenTracing.tag("http.status_code", response.code.code)
}

ZioTelemetryOpenTracingBackend(zioBackend, sttpTracer)
```


