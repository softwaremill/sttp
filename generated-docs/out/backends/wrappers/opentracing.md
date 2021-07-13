# Opentracing backend 

To use, add the following dependency to your project:

```
"com.softwaremill.sttp.client3" %% "opentracing-backend" % "3.3.10"
```

This backend depends on [opentracing](https://github.com/opentracing/opentracing-java), a standardized set of api for distributed tracing.

The opentracing backend wraps any other backend, but it's useless without a concrete distributed tracing implementation. To obtain instance of opentracing backend:

```scala
OpenTracingBackend(wrappedBackend, tracer)
```

Where tracer is an interface which can be implemented by any compatible library. See examples below.

The backend obtains the current trace context using default spans's propagation mechanisms. 

There is an additional method exposed to override default operation id:

```scala
import sttp.client3._
import sttp.client3.opentracing.OpenTracingBackend._

basicRequest
  .get(???)
  .tagWithOperationId("register-user")
```

There is an additional method exposed to customize generated span:

```scala
import sttp.client3._
import sttp.client3.opentracing.OpenTracingBackend._

basicRequest
  .get(???)
  .tagWithTransformSpan(_.setTag("custom-tag", "custom-value").setOperationName("new-name").log("my-event"))
```

## Integration with jaeger

Using with [jaeger](https://www.jaegertracing.io/) tracing

Add following dependency:

```
libraryDependencies += "io.jaegertracing" % "jaeger-client" % "1.6.0"
```

Create an instance of tracer:

```scala
import io.opentracing.Tracer
import io.jaegertracing.Configuration
import io.jaegertracing.Configuration.ReporterConfiguration
import io.jaegertracing.Configuration.SamplerConfiguration

def initTracer(serviceName: String ): Tracer = {
  val samplerConfig = SamplerConfiguration.fromEnv().withType("const").withParam(1)
  val reporterConfig = ReporterConfiguration.fromEnv().withLogSpans(true)
  val config = new Configuration(serviceName).withSampler(samplerConfig)
                    .withReporter(reporterConfig)
  config.getTracer()
}
```          

For more details about integration with jaeger click [here](https://github.com/jaegertracing/jaeger-client-java)

## Integration with brave

Using with [brave](https://github.com/openzipkin/brave) tracing

Add following dependency:

```
libraryDependencies += "io.opentracing.brave" % "brave-opentracing" % "1.0.0"
// and for integrationw with okHttp:
libraryDependencies += "io.zipkin.reporter2" % "zipkin-sender-okhttp3" % "2.16.3" 
```

Create an instance of tracer:

```scala
import io.opentracing.Tracer
import zipkin2.reporter.AsyncReporter
import zipkin2.reporter.okhttp3.OkHttpSender
import brave.propagation.{ExtraFieldPropagation, B3Propagation}
import brave.Tracing
import brave.opentracing.BraveTracer
import java.util.Arrays

def initTracer(zipkinUrl: String, serviceName: String): Tracer = {
  // Configure a reporter, which controls how often spans are sent
  val sender = OkHttpSender.create(zipkinUrl)
  val spanReporter = AsyncReporter.create(sender)

  // If you want to support baggage, indicate the fields you'd like to
  // whitelist, in this case "country-code" and "user-id". On the wire,
  // they will be prefixed like "baggage-country-code"
  val propagationFactory = ExtraFieldPropagation.newFactoryBuilder(B3Propagation.FACTORY)
                                  .addPrefixedFields("baggage-",
                                          Arrays.asList("country-code", "user-id"))
                                  .build()

  // Now, create a Brave tracing component with the service name you want to see in
  // Zipkin (the dependency is io.zipkin.brave:brave).
  val braveTracing = Tracing.newBuilder()
                        .localServiceName(serviceName)
                        .propagationFactory(propagationFactory)
                        .spanReporter(spanReporter)
                        .build()

  // use this to create an OpenTracing Tracer
  BraveTracer.create(braveTracing)
}
```

For more details about integration with brave click [here](https://github.com/openzipkin-contrib/brave-opentracing)
