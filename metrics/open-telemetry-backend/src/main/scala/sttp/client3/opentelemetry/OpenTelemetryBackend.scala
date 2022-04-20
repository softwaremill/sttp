package sttp.client3.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.{AttributeKey, Attributes}
import io.opentelemetry.api.trace.{Span, SpanKind, StatusCode}
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.{TextMapPropagator, TextMapSetter}
import sttp.capabilities.Effect
import sttp.client3._
import sttp.monad.MonadError
import sttp.monad.syntax._

import scala.collection.mutable

private class OpenTelemetryBackend[F[_], P](
    delegate: SttpBackend[F, P],
    openTelemetry: OpenTelemetry
) extends SttpBackend[F, P] {

  private val tracer = openTelemetry.getTracer("sttp3-client", "1.0.0")
  private implicit val _monad: MonadError[F] = responseMonad
  type PE = P with Effect[F]

  def send[T, R >: PE](request: Request[T, R]): F[Response[T]] = {
    val carrier: mutable.Map[String, String] = mutable.Map().empty
    val propagator: TextMapPropagator = openTelemetry.getPropagators.getTextMapPropagator
    val setter: TextMapSetter[mutable.Map[String, String]] = (carrier, key, value) => carrier.update(key, value)
    responseMonad
      .eval {
        val attributes = prepareBaseAttributes(request)
        val span: Span = tracer
          .spanBuilder(s"HTTP ${request.method.method}")
          .setSpanKind(SpanKind.CLIENT)
          .setAllAttributes(attributes)
          .startSpan
        val scope = span.makeCurrent()
        propagator.inject(Context.current(), carrier, setter)
        (span, scope)
      }
      .flatMap { spanAndScope =>
        val (span, scope) = spanAndScope
        val label = requestLabel(request)
        Span.current.addEvent(s"Start handling request: $label")
        responseMonad.handleError(
          delegate.send(request.headers(carrier.toMap)).map { response =>
            span.setAttribute(AttributeKey.stringKey("http.status_code"), response.code.code.toString)
            Span.current.addEvent(s"Request $label handle with ${response.code.code}")
            span.end()
            scope.close()
            response
          }
        ) { case e =>
          span
            .recordException(e)
            .setStatus(StatusCode.ERROR)
            .end()
          scope.close()
          responseMonad.error(e)
        }
      }
  }

  private def prepareBaseAttributes[R >: PE, T](request: Request[T, R]) = {
    Attributes.of(
      AttributeKey.stringKey("http.method"),
      request.method.method,
      AttributeKey.stringKey("http.url"),
      request.uri.toString(),
      AttributeKey.stringKey("component"),
      "sttp3-client"
    )
  }

  private def requestLabel[R >: PE, T](request: Request[T, R]): String = {
    s"${request.method.method}-${request.uri.path}"
  }

  override def close(): F[Unit] = delegate.close()

  override def responseMonad: MonadError[F] = delegate.responseMonad

}

object OpenTelemetryBackend {
  def apply[F[_], P](
      delegate: SttpBackend[F, P],
      openTelemetry: OpenTelemetry
  ): SttpBackend[F, P] =
    new OpenTelemetryBackend[F, P](delegate, openTelemetry)

}
