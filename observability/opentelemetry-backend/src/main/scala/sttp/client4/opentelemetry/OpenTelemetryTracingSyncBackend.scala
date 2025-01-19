package sttp.client4.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapSetter
import sttp.capabilities.Effect
import sttp.client4.GenericRequest
import sttp.client4.Response
import sttp.client4.ResponseException
import sttp.client4.SyncBackend
import sttp.client4.wrappers.DelegateBackend
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.shared.Identity

import scala.collection.mutable

class OpenTelemetryTracingSyncBackend(delegate: SyncBackend, config: OpenTelemetryTracingSyncConfig)
    extends DelegateBackend(delegate)
    with SyncBackend {

  private val setter = new TextMapSetter[mutable.Map[String, String]] {
    def set(carrier: mutable.Map[String, String], key: String, value: String): Unit = {
      val _ = carrier.put(key, value)
    }
  }

  override def send[T](request: GenericRequest[T, Any with Effect[Identity]]): Identity[Response[T]] = {
    val span = config.tracer
      .spanBuilder(config.spanName(request))
      .setAllAttributes(config.requestAttributes(request))
      .startSpan()

    try {
      val scope = span.makeCurrent()
      try {
        val carrier = mutable.Map.empty[String, String]
        config.propagators.getTextMapPropagator().inject(Context.current(), carrier, setter)

        val requestWithTraceContext = request.headers(carrier.toMap)

        try {
          val response = delegate.send(requestWithTraceContext)
          span.setAllAttributes(config.responseAttributes(request, response))
          response
        } catch {
          case e: Exception =>
            ResponseException.find(e) match {
              case Some(re) =>
                span.setAllAttributes(
                  config.responseAttributes(request, Response((), re.response.code, request.onlyMetadata))
                )
              case _ =>
                span.setAllAttributes(config.errorAttributes(e))
            }
            throw e
        }
      } finally {
        scope.close()
      }
    } finally {
      span.end()
    }
  }
}

object OpenTelemetryTracingSyncBackend {
  def apply(delegate: SyncBackend, openTelemetry: OpenTelemetry): SyncBackend =
    apply(delegate, OpenTelemetryTracingSyncConfig(openTelemetry))

  def apply(delegate: SyncBackend, config: OpenTelemetryTracingSyncConfig): SyncBackend = {
    // redirects should be handled before tracing
    FollowRedirectsBackend(new OpenTelemetryTracingSyncBackend(delegate, config))
  }
}
