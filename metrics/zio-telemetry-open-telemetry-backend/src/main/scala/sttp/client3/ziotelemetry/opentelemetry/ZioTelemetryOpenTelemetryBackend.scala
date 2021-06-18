package sttp.client3.ziotelemetry.opentelemetry

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.{TextMapPropagator, TextMapSetter}
import sttp.capabilities.Effect
import sttp.client3._
import zio._
import zio.telemetry.opentelemetry.TracingSyntax.OpenTelemetryZioOps
import zio.telemetry.opentelemetry._

import scala.collection.mutable

private class ZioTelemetryOpenTelemetryBackend[+P] (
    delegate: SttpBackend[Task, P],
    tracer: ZioTelemetryOpenTelemetryTracer,
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
      .span(s"${request.method.method} ${request.uri.path.mkString("/")}", SpanKind.CLIENT)
      .provide(tracing)
  }
}

object ZioTelemetryOpenTelemetryBackend {
  def apply[P](
      other: SttpBackend[Task, P],
      tracing: Tracing.Service,
      tracer: ZioTelemetryOpenTelemetryTracer = ZioTelemetryOpenTelemetryTracer.empty
  ): SttpBackend[Task, P] =
    new ZioTelemetryOpenTelemetryBackend[P](other, tracer, Has(tracing))

}

trait ZioTelemetryOpenTelemetryTracer {
  def before[T](request: Request[T, Nothing]): RIO[Tracing, Unit]
  def after[T](response: Response[T]): RIO[Tracing, Unit]
}

object ZioTelemetryOpenTelemetryTracer {
  val empty: ZioTelemetryOpenTelemetryTracer = new ZioTelemetryOpenTelemetryTracer {
    def before[T](request: Request[T, Nothing]): RIO[Tracing, Unit] = ZIO.unit
    def after[T](response: Response[T]): RIO[Tracing, Unit] = ZIO.unit
  }
}
