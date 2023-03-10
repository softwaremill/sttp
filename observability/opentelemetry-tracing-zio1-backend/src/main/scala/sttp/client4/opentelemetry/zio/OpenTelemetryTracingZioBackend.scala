package sttp.client4.opentelemetry.zio

import io.opentelemetry.api.trace.{SpanKind, StatusCode}
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.{TextMapPropagator, TextMapSetter}
import sttp.capabilities.Effect
import sttp.client4._
import zio._
import zio.telemetry.opentelemetry.TracingSyntax.OpenTelemetryZioOps
import zio.telemetry.opentelemetry._

import scala.collection.mutable

private class OpenTelemetryTracingZioBackend[+P](
                                                  delegate: GenericBackend[Task, P],
                                                  tracer: OpenTelemetryZioTracer,
                                                  tracing: Tracing
) extends DelegateBackend[Task, P](delegate)
    with Backend[Task] {
  def send[T](request: GenericRequest[T, P with Effect[Task]]): Task[Response[T]] = {
    val carrier: mutable.Map[String, String] = mutable.Map().empty
    val propagator: TextMapPropagator = W3CTraceContextPropagator.getInstance()
    val setter: TextMapSetter[mutable.Map[String, String]] = (carrier, key, value) => carrier.update(key, value)

    (for {
      _ <- Tracing.inject(propagator, carrier, setter)
      _ <- tracer.before(request)
      resp <- delegate.send(request.headers(carrier.toMap))
      _ <- tracer.after(resp)
    } yield resp)
      .span(tracer.spanName(request), SpanKind.CLIENT, { case _ => StatusCode.ERROR })
      .provide(tracing)
  }
}

object OpenTelemetryTracingZioBackend {
  def apply(other: Backend[Task], tracing: Tracing.Service): Backend[Task] =
    apply(other, tracing, OpenTelemetryZioTracer.Default)

  def apply(other: WebSocketBackend[Task], tracing: Tracing.Service): WebSocketBackend[Task] =
    apply(other, tracing, OpenTelemetryZioTracer.Default)

  def apply[S](other: StreamBackend[Task, S], tracing: Tracing.Service): StreamBackend[Task, S] =
    apply(other, tracing, OpenTelemetryZioTracer.Default)

  def apply[S](other: WebSocketStreamBackend[Task, S], tracing: Tracing.Service): WebSocketStreamBackend[Task, S] =
    apply(other, tracing, OpenTelemetryZioTracer.Default)

  def apply(other: Backend[Task], tracing: Tracing.Service, tracer: OpenTelemetryZioTracer): Backend[Task] =
    new OpenTelemetryTracingZioBackend(other, tracer, Has(tracing))

  def apply(
      other: WebSocketBackend[Task],
      tracing: Tracing.Service,
      tracer: OpenTelemetryZioTracer
  ): WebSocketBackend[Task] =
    new OpenTelemetryTracingZioBackend(other, tracer, Has(tracing)) with WebSocketBackend[Task]

  def apply[S](
      other: StreamBackend[Task, S],
      tracing: Tracing.Service,
      tracer: OpenTelemetryZioTracer
  ): StreamBackend[Task, S] =
    new OpenTelemetryTracingZioBackend(other, tracer, Has(tracing)) with StreamBackend[Task, S]

  def apply[S](
      other: WebSocketStreamBackend[Task, S],
      tracing: Tracing.Service,
      tracer: OpenTelemetryZioTracer
  ): WebSocketStreamBackend[Task, S] =
    new OpenTelemetryTracingZioBackend(other, tracer, Has(tracing)) with WebSocketStreamBackend[Task, S]

}

trait OpenTelemetryZioTracer {
  def spanName[T](request: GenericRequest[T, Nothing]): String
  def before[T](request: GenericRequest[T, Nothing]): RIO[Tracing, Unit]
  def after[T](response: Response[T]): RIO[Tracing, Unit]
}

object OpenTelemetryZioTracer {
  val Default: OpenTelemetryZioTracer = new OpenTelemetryZioTracer {
    override def spanName[T](request: GenericRequest[T, Nothing]): String = s"HTTP ${request.method.method}"
    override def before[T](request: GenericRequest[T, Nothing]): RIO[Tracing, Unit] =
      Tracing.setAttribute("http.method", request.method.method) *>
        Tracing.setAttribute("http.url", request.uri.toString()) *>
        ZIO.unit
    override def after[T](response: Response[T]): RIO[Tracing, Unit] =
      Tracing.setAttribute("http.status_code", response.code.code.toLong) *>
        ZIO.unit
  }
}
