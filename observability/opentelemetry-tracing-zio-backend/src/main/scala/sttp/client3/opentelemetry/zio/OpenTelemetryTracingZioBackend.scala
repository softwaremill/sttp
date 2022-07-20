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
    tracer: OpenTelemetryZioTracer,
    tracing: Tracing
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
      tracing: Tracing,
      tracer: OpenTelemetryZioTracer = OpenTelemetryZioTracer.Default
  ): SttpBackend[Task, P] =
    new OpenTelemetryTracingZioBackend[P](other, tracer, tracing)

}

trait OpenTelemetryZioTracer {
  def spanName[T](request: Request[T, Nothing]): String
  def before[T](request: Request[T, Nothing]): RIO[Tracing, Unit]
  def after[T](response: Response[T]): RIO[Tracing, Unit]
}

object OpenTelemetryZioTracer {
  val Default: OpenTelemetryZioTracer = new OpenTelemetryZioTracer {
    override def spanName[T](request: Request[T, Nothing]): String = s"HTTP ${request.method.method}"
    override def before[T](request: Request[T, Nothing]): RIO[Tracing, Unit] =
      Tracing.setAttribute("http.method", request.method.method) *>
        Tracing.setAttribute("http.url", request.uri.toString()) *>
        ZIO.unit
    override def after[T](response: Response[T]): RIO[Tracing, Unit] =
      Tracing.setAttribute("http.status_code", response.code.code.toLong) *>
        ZIO.unit
  }
}
