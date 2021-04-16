package sttp.client3.ziotelemetry.opentelemetry

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.propagation.{TextMapPropagator, TextMapSetter}
import sttp.capabilities.Effect
import sttp.client3._
import sttp.client3.impl.zio.ExtendEnv
import zio._
import zio.telemetry.opentelemetry.TracingSyntax.OpenTelemetryZioOps
import zio.telemetry.opentelemetry._

import scala.collection.mutable

class ZioTelemetryOpenTelemetryBackend[+P] private (
    delegate: SttpBackend[RIO[Tracing, *], P],
    tracer: ZioTelemetryOpenTelemetryTracer
) extends DelegateSttpBackend[RIO[Tracing, *], P](delegate) {
  def send[T, R >: P with Effect[RIO[Tracing, *]]](request: Request[T, R]): RIO[Tracing, Response[T]] = {
    val carrier: mutable.Map[String, String] = mutable.Map().empty
    val propagator: TextMapPropagator = W3CTraceContextPropagator.getInstance()
    val setter: TextMapSetter[mutable.Map[String, String]] = (carrier, key, value) => carrier.update(key, value)
    Tracing.inject(propagator, carrier, setter).flatMap { _ =>
      (for {
        _ <- tracer.before(request)
        resp <- delegate.send(request.headers(carrier.toMap))
        _ <- tracer.after(resp)
      } yield resp).span(s"${request.method.method} ${request.uri.path.mkString("/")}", SpanKind.CLIENT)
    }
  }
}

object ZioTelemetryOpenTelemetryBackend {
  def apply[P](
      other: SttpBackend[Task, P],
      tracer: ZioTelemetryOpenTelemetryTracer = ZioTelemetryOpenTelemetryTracer.empty
  ): SttpBackend[RIO[Tracing, *], P] = {
    new ZioTelemetryOpenTelemetryBackend[P](other.extendEnv[Tracing], tracer)
  }
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
