package sttp.client.ziotelemetry.opentracing

import io.opentracing.propagation.{Format, TextMapAdapter}

import scala.jdk.CollectionConverters._
import sttp.client._
import sttp.client.impl.zio.RIOMonadAsyncError
import sttp.client.monad.MonadError
import sttp.client.ws.WebSocketResponse
import zio._
import zio.telemetry.opentracing._

class ZioTelemetryOpenTracingBackend[-WS_HANLDER[_]](
    other: SttpBackend[Task, Nothing, WS_HANLDER],
    tracer: ZioTelemetryOpenTracingTracer = ZioTelemetryOpenTracingTracer.empty
) extends SttpBackend[RIO[OpenTracing, *], Any, WS_HANLDER] {

  @SuppressWarnings(Array("scalafix:Disable.toString"))
  def send[T, R >: Any with Effect[Task]](request: Request[T, R]): RIO[OpenTracing, Response[T]] = {
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

  def openWebsocket[T, WS_RESULT, R >: Any with Effect[Task]](
      request: Request[T, R],
      handler: WS_HANLDER[WS_RESULT]
  ): RIO[OpenTracing, WebSocketResponse[WS_RESULT]] =
    other.openWebsocket(request, handler)

  def close(): RIO[OpenTracing, Unit] = other.close()

  def responseMonad: MonadError[RIO[OpenTracing, *]] = new RIOMonadAsyncError[OpenTracing]
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
