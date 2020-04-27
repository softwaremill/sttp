# zio-telemetry opentracing backend 

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client" %% "zio-telemetry-opentracing-backend" % "2.1.0-RC1"
```

This backend depends on [zio-opentracing](https://github.com/zio/zio-telemetry).

The opentracing backend wraps a `Task` based ZIO backend and yields a backend of `Backend[RIO[OpenTracing, *], Nothing, WS_HANDLER]`. The yielded effects are of type `RIO[OpenTracing, *]` which mean they can be a child of a other span created in your ZIO program.

Here's how you construct `ZioTelemetryOpenTracingBackend`. I would recommend wrapping this is in `ZLayer`

```scala
new ZioTelemetryOpenTracingBackend(zioBackend)
```

We already append tags like `http.method`, `http.url` and `http.status_code`

Additionally you can add tags per request by supplying a `RequestTagCollector`

```scala
val tagMethod: RequestTagCollector = new RequestTagCollector {
  def collect[T](request: Request[T, Nothing]): Map[String, String] = Map("method" -> request.method.method)
}

new ZioTelemetryOpenTracingBackend(zioBackend, tagMethod)
```


