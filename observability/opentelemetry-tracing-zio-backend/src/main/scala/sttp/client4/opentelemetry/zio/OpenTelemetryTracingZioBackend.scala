package sttp.client4.opentelemetry.zio

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.semconv.{HttpAttributes, UrlAttributes}
import sttp.capabilities.Effect
import sttp.client4._
import sttp.client4.wrappers.DelegateBackend
import zio._
import zio.telemetry.opentelemetry.context.OutgoingContextCarrier
import zio.telemetry.opentelemetry.tracing.Tracing
import zio.telemetry.opentelemetry.tracing.propagation.TraceContextPropagator

abstract class OpenTelemetryTracingZioBackend[+P](
    delegate: GenericBackend[Task, P],
    tracer: OpenTelemetryTracer,
    tracing: Tracing
) extends DelegateBackend[Task, P](delegate)
    with Backend[Task] {
  def send[T](request: GenericRequest[T, P with Effect[Task]]): Task[Response[T]] =
    ZIO.scoped {
      val carrier = OutgoingContextCarrier.default()
      for {
        _ <- tracing.spanScoped(tracer.spanName(request), SpanKind.CLIENT, tracer.requestAttributes(request))
        _ <- tracing.injectSpan(TraceContextPropagator.default, carrier)
        resp <- delegate.send(request.headers(carrier.kernel.toMap))
        _ <- ZIO.addFinalizer(tracing.getCurrentSpanUnsafe.map(_.setAllAttributes(tracer.responseAttributes(resp))))
      } yield resp
    }
}

object OpenTelemetryTracingZioBackend {
  def apply(other: Backend[Task], tracing: Tracing): Backend[Task] =
    apply(other, tracing, OpenTelemetryTracer.Default)

  def apply(other: WebSocketBackend[Task], tracing: Tracing): WebSocketBackend[Task] =
    apply(other, tracing, OpenTelemetryTracer.Default)

  def apply[S](other: StreamBackend[Task, S], tracing: Tracing): StreamBackend[Task, S] =
    apply(other, tracing, OpenTelemetryTracer.Default)

  def apply[S](other: WebSocketStreamBackend[Task, S], tracing: Tracing): WebSocketStreamBackend[Task, S] =
    apply(other, tracing, OpenTelemetryTracer.Default)

  def apply(other: Backend[Task], tracing: Tracing, tracer: OpenTelemetryTracer): Backend[Task] =
    new OpenTelemetryTracingZioBackend(other, tracer, tracing) with Backend[Task]

  def apply(other: WebSocketBackend[Task], tracing: Tracing, tracer: OpenTelemetryTracer): WebSocketBackend[Task] =
    new OpenTelemetryTracingZioBackend(other, tracer, tracing) with WebSocketBackend[Task]

  def apply[S](
      other: StreamBackend[Task, S],
      tracing: Tracing,
      tracer: OpenTelemetryTracer
  ): StreamBackend[Task, S] =
    new OpenTelemetryTracingZioBackend(other, tracer, tracing) with StreamBackend[Task, S]

  def apply[S](
      other: WebSocketStreamBackend[Task, S],
      tracing: Tracing,
      tracer: OpenTelemetryTracer
  ): WebSocketStreamBackend[Task, S] =
    new OpenTelemetryTracingZioBackend(other, tracer, tracing) with WebSocketStreamBackend[Task, S]
}

trait OpenTelemetryTracer {
  def spanName[T](request: GenericRequest[T, Nothing]): String
  def requestAttributes[T](request: GenericRequest[T, Nothing]): Attributes
  def responseAttributes[T](response: Response[T]): Attributes
}

object OpenTelemetryTracer {
  lazy val Default: OpenTelemetryTracer = new OpenTelemetryTracer {
    override def spanName[T](request: GenericRequest[T, Nothing]): String = s"HTTP ${request.method.method}"
    override def requestAttributes[T](request: GenericRequest[T, Nothing]): Attributes =
      Attributes.builder
        .put(HttpAttributes.HTTP_REQUEST_METHOD, request.method.method)
        .put(UrlAttributes.URL_FULL, request.uri.toString())
        .build()

    override def responseAttributes[T](response: Response[T]): Attributes =
      Attributes.builder
        .put(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, response.code.code.toLong: java.lang.Long)
        .build()
  }
}
