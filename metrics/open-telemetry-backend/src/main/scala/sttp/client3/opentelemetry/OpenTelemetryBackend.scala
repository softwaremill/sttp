package sttp.client3.opentelemetry

import io.opentelemetry.api.common.{AttributeKey, Attributes}
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.api.trace.{Span, SpanKind, StatusCode, Tracer}
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.{TextMapPropagator, TextMapSetter}
import sttp.capabilities.Effect
import sttp.client3._
import sttp.monad.MonadError
import sttp.monad.syntax._

import scala.collection.mutable

private class OpenTelemetryBackend[F[_], P](
    delegate: SttpBackend[F, P],
    tracer: Tracer
) extends SttpBackend[F, P] {

  private implicit val _monad: MonadError[F] = responseMonad
  type PE = P with Effect[F]

  def send[T, R >: PE](request: Request[T, R]): F[Response[T]] = {
    val carrier: mutable.Map[String, String] = mutable.Map().empty
    val propagator: TextMapPropagator = W3CTraceContextPropagator.getInstance()
    val setter: TextMapSetter[mutable.Map[String, String]] = (carrier, key, value) => carrier.update(key, value)
    responseMonad
      .eval {
        val span: Span = tracer
          .spanBuilder(s"HTTP ${request.method.method}")
          .setSpanKind(SpanKind.CLIENT)
          .startSpan
        val ignored = span.makeCurrent()
        propagator.inject(Context.current(), carrier, setter)
        val attributes = Attributes.of(
          AttributeKey.stringKey("http.method"),
          request.method.method,
          AttributeKey.stringKey("http.url"),
          request.uri.toString(),
          AttributeKey.stringKey("component"),
          "sttp3-client"
        )
        Span.current.addEvent("Starting the work.", attributes)
        (span, ignored)
      }
      .flatMap { span =>
        responseMonad.handleError(
          delegate.send(request.headers(carrier.toMap)).map { response =>
            val attributes = Attributes.of(AttributeKey.stringKey("http.status_code"), response.code.code.toString)
            Span.current.addEvent("Finished working.", attributes)
            span._1.end()
            span._2.close()
            response
          }
        ) { case e =>
          span._1
            .recordException(e)
            .setStatus(StatusCode.ERROR)
            .end()
          span._2.close()
          responseMonad.error(e)
        }
      }
  }

  override def close(): F[Unit] = delegate.close()

  override def responseMonad: MonadError[F] = delegate.responseMonad

}

object OpenTelemetryBackend {
  def apply[F[_], P](
      delegate: SttpBackend[F, P],
      tracer: Tracer
  ): SttpBackend[F, P] =
    new OpenTelemetryBackend[F, P](delegate, tracer)

}
