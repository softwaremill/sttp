package sttp.client.ziotelemetry.opentracing

import io.opentracing.propagation.{Format, TextMapAdapter}
import sttp.capabilities.Effect

import scala.jdk.CollectionConverters._
import sttp.client._
import sttp.client.impl.zio.{ExtendEnv, RIOMonadAsyncError}
import sttp.monad.MonadError
import zio._
import zio.telemetry.opentracing._

class ZioTelemetryOpenTracingBackend[+P] private (
    other: SttpBackend[RIO[OpenTracing, *], P],
    tracer: ZioTelemetryOpenTracingTracer
) extends SttpBackend[RIO[OpenTracing, *], P] {
  def send[T, R >: P with Effect[RIO[OpenTracing, *]]](request: Request[T, R]): RIO[OpenTracing, Response[T]] = {
    val headers = scala.collection.mutable.Map.empty[String, String]
    val buffer = new TextMapAdapter(headers.asJava)
    OpenTracing.inject(Format.Builtin.HTTP_HEADERS, buffer).flatMap { _ =>
      (for {
        _ <- tracer.before(request)
        resp <- other.send(request.headers(headers.toMap))
        _ <- tracer.after(resp)
      } yield resp).span(s"${request.method.method} ${request.uri.path.mkString("/")}")
    }
  }

  def close(): RIO[OpenTracing, Unit] = other.close()

  val responseMonad: MonadError[RIO[OpenTracing, *]] = new RIOMonadAsyncError[OpenTracing]
}

object ZioTelemetryOpenTracingBackend {
  def apply[P](
      other: SttpBackend[Task, P],
      tracer: ZioTelemetryOpenTracingTracer = ZioTelemetryOpenTracingTracer.empty
  ): SttpBackend[RIO[OpenTracing, *], P] = {
    new ZioTelemetryOpenTracingBackend[P](other.extendEnv[OpenTracing], tracer)
  }
}

trait ZioTelemetryOpenTracingTracer {
  def before[T](request: Request[T, Nothing]): RIO[OpenTracing, Unit]
  def after[T](response: Response[T]): RIO[OpenTracing, Unit]
}

object ZioTelemetryOpenTracingTracer {
  val empty: ZioTelemetryOpenTracingTracer = new ZioTelemetryOpenTracingTracer {
    def before[T](request: Request[T, Nothing]): RIO[OpenTracing, Unit] = ZIO.unit
    def after[T](response: Response[T]): RIO[OpenTracing, Unit] = ZIO.unit
  }
}
