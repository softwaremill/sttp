package sttp.client4.opentelemetry

import sttp.client4.GenericRequest
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.UrlAttributes
import io.opentelemetry.semconv.ErrorAttributes
import io.opentelemetry.semconv.ServerAttributes
import io.opentelemetry.api.common.AttributesBuilder
import sttp.model.ResponseMetadata

object OpenTelemetryDefaults {

  /** @see https://opentelemetry.io/docs/specs/semconv/http/http-spans/#name */
  def spanName(request: GenericRequest[_, _]): String = s"${request.method.method}"

  /** @see https://opentelemetry.io/docs/specs/semconv/http/http-metrics/#http-client */
  def requestAttributes(request: GenericRequest[_, _]): Attributes = requestAttributesBuilder(request).build()

  /** @see
    *   https://opentelemetry.io/docs/specs/semconv/http/http-spans/#http-client (full url is required for tracing, but
    *   not for metrics)
    */
  def requestAttributesWithFullUrl(request: GenericRequest[_, _]): Attributes =
    requestAttributesBuilder(request).put(UrlAttributes.URL_FULL, request.uri.toString()).build()

  private def requestAttributesBuilder(request: GenericRequest[_, _]): AttributesBuilder =
    Attributes.builder
      .put(HttpAttributes.HTTP_REQUEST_METHOD, request.method.method)
      .put(ServerAttributes.SERVER_ADDRESS, request.uri.host.getOrElse("unknown"))
      .put(ServerAttributes.SERVER_PORT, request.uri.port.getOrElse(80))

  /** @see https://opentelemetry.io/docs/specs/semconv/http/http-metrics/#http-client */
  def responseAttributes(request: GenericRequest[_, _], response: ResponseMetadata): Attributes =
    Attributes.builder
      .put(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, response.code.code.toLong: java.lang.Long)
      .build()

  def errorAttributes(e: Throwable): Attributes = {
    val errorType = e match {
      case _: java.net.UnknownHostException => "unknown_host"
      case _                                => e.getClass.getSimpleName
    }
    Attributes.builder().put(ErrorAttributes.ERROR_TYPE, errorType).build()
  }

  val instrumentationScopeName = "sttp-client4"

  val instrumentationScopeVersion = "1.0.0"
}
