package sttp.client4.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.propagation.ContextPropagators
import sttp.client4._

import java.time.Clock

case class OpenTelemetryTracingSyncConfig(
    tracer: Tracer,
    propagators: ContextPropagators,
    clock: Clock,
    spanName: GenericRequest[_, _] => String,
    requestAttributes: GenericRequest[_, _] => Attributes,
    responseAttributes: (GenericRequest[_, _], Response[_]) => Attributes,
    errorAttributes: Throwable => Attributes
)

object OpenTelemetryTracingSyncConfig {
  def apply(
      openTelemetry: OpenTelemetry,
      clock: Clock = Clock.systemUTC(),
      spanName: GenericRequest[_, _] => String = OpenTelemetryDefaults.spanName _,
      requestAttributes: GenericRequest[_, _] => Attributes = OpenTelemetryDefaults.requestAttributesWithFullUrl _,
      responseAttributes: (GenericRequest[_, _], Response[_]) => Attributes =
        OpenTelemetryDefaults.responseAttributes _,
      errorAttributes: Throwable => Attributes = OpenTelemetryDefaults.errorAttributes _
  ): OpenTelemetryTracingSyncConfig = usingTracer(
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
      spanName: GenericRequest[_, _] => String = OpenTelemetryDefaults.spanName _,
      requestAttributes: GenericRequest[_, _] => Attributes = OpenTelemetryDefaults.requestAttributesWithFullUrl _,
      responseAttributes: (GenericRequest[_, _], Response[_]) => Attributes =
        OpenTelemetryDefaults.responseAttributes _,
      errorAttributes: Throwable => Attributes = OpenTelemetryDefaults.errorAttributes _
  ): OpenTelemetryTracingSyncConfig =
    OpenTelemetryTracingSyncConfig(
      tracer,
      propagators,
      clock,
      spanName,
      requestAttributes,
      responseAttributes,
      errorAttributes
    )
}
