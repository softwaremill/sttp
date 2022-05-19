package sttp.client3.opentelemetry.zio

import io.opentelemetry.api.trace.{SpanKind, StatusCode}
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.{TextMapPropagator, TextMapSetter}
import sttp.capabilities.Effect
import sttp.client3._
import zio._
import zio.telemetry.opentelemetry.TracingSyntax.OpenTelemetryZioOps
import zio.telemetry.opentelemetry._

import scala.collection.mutable

private class OpenTelemetryTracingZioBackend[+P](
    delegate: SttpBackend[Task, P],
    tracer: ZioTelemetryOpenTelemetryTracer,
    tracing: Tracing.Service
) extends DelegateSttpBackend[Task, P](delegate) {
  def send[T, R >: P with Effect[Task]](request: Request[T, R]): Task[Response[T]] = {
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
      .provideLayer(ZLayer.succeed(tracing))
  }
}

object OpenTelemetryTracingZioBackend {
  def apply[P](
      other: SttpBackend[Task, P],
      tracing: Tracing.Service,
      tracer: ZioTelemetryOpenTelemetryTracer = ZioTelemetryOpenTelemetryTracer.empty
  ): SttpBackend[Task, P] =
    new OpenTelemetryTracingZioBackend[P](other, tracer, tracing)

}

trait ZioTelemetryOpenTelemetryTracer {
  def spanName[T](request: Request[T, Nothing]): String = s"HTTP ${request.method.method}"
  def before[T](request: Request[T, Nothing]): RIO[Tracing.Service, Unit]
  def after[T](response: Response[T]): RIO[Tracing.Service, Unit]
}

object ZioTelemetryOpenTelemetryTracer {
  val empty: ZioTelemetryOpenTelemetryTracer = new ZioTelemetryOpenTelemetryTracer {
    def before[T](request: Request[T, Nothing]): RIO[Tracing.Service, Unit] = ZIO.unit
    def after[T](response: Response[T]): RIO[Tracing.Service, Unit] = ZIO.unit
  }
}
