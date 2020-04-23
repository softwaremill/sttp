# brave backend (deprecated)

Since 2.0.9 brave-backend is deprecated, you should use [opentracing backend](opentracing.html) with brave integration.

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client" %% "brave-backend" % "2.0.9"
```

This backend depends on [brave](https://github.com/openzipkin/brave), a distributed tracing implementation compatible with Zipkin backend services.

The brave backend wraps any other backend, and needs an instance of brave's `HttpTracing` or `Tracing`, for example:

```scala
val httpTracing: HttpTracing = ...
implicit val sttpBackend = BraveBackend(AkkaHttpBackend(), httpTracing)
```

The backend obtains the current trace context using default Brave's propagation mechanisms. As it's often challenging to integrate context propagation in an asynchronous setting, there's also a possibility to add the trace context to the request's tags:

```
import sttp.client.brave.BraveBackend._

val parent: TraceContext = ...

basicRequest
  .get(...)
  .tagWithTraceContext(parent))
```