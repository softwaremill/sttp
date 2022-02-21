package sttp.client3.ziotelemetry.opentracing

import io.opentracing.propagation.{Format, TextMapAdapter}
import sttp.capabilities.Effect
import sttp.client3._
import sttp.client3.impl.zio.ExtendEnv
import zio._
import zio.telemetry.opentracing._

import scala.jdk.CollectionConverters._

class ZioTelemetryOpenTracingBackend[+P] private (
    delegate: SttpBackend[RIO[OpenTracing.Service, *], P],
    tracer: ZioTelemetryOpenTracingTracer
) extends DelegateSttpBackend[RIO[OpenTracing.Service, *], P](delegate) {
  def send[T, R >: P with Effect[RIO[OpenTracing.Service, *]]](
      request: Request[T, R]
  ): RIO[OpenTracing.Service, Response[T]] = {
    val headers = scala.collection.mutable.Map.empty[String, String]
    val buffer = new TextMapAdapter(headers.asJava)
    OpenTracing.inject(Format.Builtin.HTTP_HEADERS, buffer).flatMap { _ =>
      (for {
        _ <- tracer.before(request)
        resp <- delegate.send(request.headers(headers.toMap))
        _ <- tracer.after(resp)
      } yield resp).span(s"${request.method.method} ${request.uri.path.mkString("/")}")
    }
  }
}

object ZioTelemetryOpenTracingBackend {
  def apply[P](
      other: SttpBackend[Task, P],
      tracer: ZioTelemetryOpenTracingTracer = ZioTelemetryOpenTracingTracer.empty
  ): SttpBackend[RIO[OpenTracing.Service, *], P] = {
    new ZioTelemetryOpenTracingBackend[P](other.extendEnv[OpenTracing.Service], tracer)
  }
}

trait ZioTelemetryOpenTracingTracer {
  def before[T](request: Request[T, Nothing]): RIO[OpenTracing.Service, Unit]
  def after[T](response: Response[T]): RIO[OpenTracing.Service, Unit]
}

object ZioTelemetryOpenTracingTracer {
  val empty: ZioTelemetryOpenTracingTracer = new ZioTelemetryOpenTracingTracer {
    def before[T](request: Request[T, Nothing]): RIO[OpenTracing.Service, Unit] = ZIO.unit
    def after[T](response: Response[T]): RIO[OpenTracing.Service, Unit] = ZIO.unit
  }
}
