package sttp.client4.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.propagation.ContextPropagators
import sttp.client4._

import java.time.Clock

case class OpenTelemetryTracingConfig(
    tracer: Tracer,
    propagators: ContextPropagators,
    clock: Clock,
    spanName: GenericRequest[?, ?] => String,
    requestAttributes: GenericRequest[?, ?] => Attributes,
    responseAttributes: (GenericRequest[?, ?], Response[?]) => Attributes,
    errorAttributes: Throwable => Attributes
)

object OpenTelemetryTracingConfig {
  def apply(
      openTelemetry: OpenTelemetry,
      clock: Clock = Clock.systemUTC(),
      spanName: GenericRequest[?, ?] => String = OpenTelemetryDefaults.spanName,
      requestAttributes: GenericRequest[?, ?] => Attributes = OpenTelemetryDefaults.requestAttributesWithFullUrl,
      responseAttributes: (GenericRequest[?, ?], Response[?]) => Attributes = OpenTelemetryDefaults.responseAttributes,
      errorAttributes: Throwable => Attributes = OpenTelemetryDefaults.errorAttributes
  ): OpenTelemetryTracingConfig = usingTracer(
    openTelemetry
      .tracerBuilder(OpenTelemetryDefaults.instrumentationScopeName)
      .setInstrumentationVersion(OpenTelemetryDefaults.instrumentationScopeVersion)
      .build(),
    openTelemetry.getPropagators(),
    clock,
    spanName = spanName,
    requestAttributes = requestAttributes,
    responseAttributes = responseAttributes,
    errorAttributes = errorAttributes
  )

  def usingTracer(
      tracer: Tracer,
      propagators: ContextPropagators,
      clock: Clock = Clock.systemUTC(),
      spanName: GenericRequest[?, ?] => String = OpenTelemetryDefaults.spanName,
      requestAttributes: GenericRequest[?, ?] => Attributes = OpenTelemetryDefaults.requestAttributesWithFullUrl,
      responseAttributes: (GenericRequest[?, ?], Response[?]) => Attributes = OpenTelemetryDefaults.responseAttributes,
      errorAttributes: Throwable => Attributes = OpenTelemetryDefaults.errorAttributes
  ): OpenTelemetryTracingConfig =
    OpenTelemetryTracingConfig(
      tracer,
      propagators,
      clock,
      spanName,
      requestAttributes,
      responseAttributes,
      errorAttributes
    )
}
