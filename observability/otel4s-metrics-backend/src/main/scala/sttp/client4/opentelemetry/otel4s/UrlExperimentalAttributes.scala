package sttp.client4.opentelemetry.otel4s

import org.typelevel.otel4s.AttributeKey

private object UrlExperimentalAttributes {
  // url.template is still experimental, so we need to wait for it to be stable before pulling it from the otel4s semconv
  val UrlTemplate: AttributeKey[String] =
    AttributeKey("url.template")
}
