package sttp.client4.opentelemetry.otel4s

import org.typelevel.otel4s.Attributes
import sttp.client4.{GenericRequest, Response}

final case class Otel4sTracingConfig(
    spanName: GenericRequest[_, _] => String,
    requestAttributes: GenericRequest[_, _] => Attributes,
    responseAttributes: Response[_] => Attributes,
    errorAttributes: Throwable => Attributes
)

object Otel4sTracingConfig {
  val default: Otel4sTracingConfig = Otel4sTracingConfig(
    request => Otel4sTracingDefaults.spanName(request),
    request => Otel4sTracingDefaults.requestAttributes(request),
    response => Otel4sTracingDefaults.responseAttributes(response),
    error => Otel4sTracingDefaults.errorAttributes(error)
  )
}
