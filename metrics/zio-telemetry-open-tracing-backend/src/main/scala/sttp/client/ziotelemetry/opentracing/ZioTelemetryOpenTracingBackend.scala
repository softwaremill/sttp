package sttp.client.ziotelemetry.opentracing

import io.opentracing.propagation.{Format, TextMapAdapter}

import scala.jdk.CollectionConverters._
import sttp.client._
import sttp.client.impl.zio.RIOMonadAsyncError
import sttp.client.monad.{FunctionK, MapEffect, MonadError}
import zio._
import zio.telemetry.opentracing._

class ZioTelemetryOpenTracingBackend[+P](
    other: SttpBackend[Task, P],
    tracer: ZioTelemetryOpenTracingTracer = ZioTelemetryOpenTracingTracer.empty
) extends SttpBackend[RIO[OpenTracing, *], P] {

  @SuppressWarnings(Array("scalafix:Disable.toString"))
  def send[T, R >: P with Effect[RIO[OpenTracing, *]]](request: Request[T, R]): RIO[OpenTracing, Response[T]] = {
    val headers = scala.collection.mutable.Map.empty[String, String]
    val buffer = new TextMapAdapter(headers.asJava)
    OpenTracing.inject(Format.Builtin.HTTP_HEADERS, buffer).flatMap { _ =>
      (for {
        _ <- tracer.before(request)
        ot <- ZIO.environment[OpenTracing]
        mappedRequest = MapEffect[RIO[OpenTracing, *], Task, Identity, T, P](
          request,
          new FunctionK[RIO[OpenTracing, *], Task] {
            override def apply[A](fa: RIO[OpenTracing, A]): Task[A] = fa.provide(ot)
          },
          new FunctionK[Task, RIO[OpenTracing, *]] {
            override def apply[A](fa: Task[A]): RIO[OpenTracing, A] = fa
          },
          responseMonad,
          other.responseMonad
        )
        resp <- other.send(mappedRequest.headers(headers.toMap))
        _ <- tracer.after(resp)
      } yield resp).span(s"${request.method.method} ${request.uri.path.mkString("/")}")
    }
  }

  def close(): RIO[OpenTracing, Unit] = other.close()

  val responseMonad: MonadError[RIO[OpenTracing, *]] = new RIOMonadAsyncError[OpenTracing]
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
