package sttp.client4.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.TextMapSetter
import sttp.capabilities.Effect
import sttp.client4.Backend
import sttp.client4.GenericBackend
import sttp.client4.GenericRequest
import sttp.client4.Response
import sttp.client4.ResponseException
import sttp.client4.StreamBackend
import sttp.client4.SyncBackend
import sttp.client4.WebSocketBackend
import sttp.client4.WebSocketStreamBackend
import sttp.client4.WebSocketSyncBackend
import sttp.client4.wrappers.DelegateBackend
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.monad.syntax._

import scala.collection.mutable

/** A backend wrapper which traces requests using OpenTelemetry.
  *
  * Span names and attributes are calculated using the provided [[OpenTelemetryTracingConfig]].
  *
  * To use, wrap your backend using this one, e.g.:
  *
  * {{{
  * val rawBackend: SyncBackend = ???
  * val openTelemetry: OpenTelemetry = ???
  *
  * val tracingBackend = OpenTelemetryTracingBackend(rawBackend, openTelemetry)
  * }}}
  *
  * Relies on the built-in OpenTelemetry Java SDK [[io.opentelemetry.context.ContextStorage]] mechanism of propagating
  * the tracing context; by default, this is using [[ThreadLocal]]s, which works with synchronous/direct-style
  * environments. [[scala.concurrent.Future]]s are supported through instrumentation provided by the OpenTelemetry
  * javaagent. For functional effect systems, usually a dedicated integration library is required.
  */
class OpenTelemetryTracingBackend[F[_], P](delegate: GenericBackend[F, P], config: OpenTelemetryTracingConfig)
    extends DelegateBackend[F, P](delegate) {

  private val setter = new TextMapSetter[mutable.Map[String, String]] {
    def set(carrier: mutable.Map[String, String], key: String, value: String): Unit = {
      val _ = carrier.put(key, value)
    }
  }

  override def send[T](request: GenericRequest[T, P with Effect[F]]): F[Response[T]] = {
    monad
      .eval {
        config.tracer
          .spanBuilder(config.spanName(request))
          .setAllAttributes(config.requestAttributes(request))
          .startSpan()
      }
      .flatMap { span =>
        monad.ensure2(
          {
            val scope = span.makeCurrent()
            try {
              val carrier = mutable.Map.empty[String, String]
              config.propagators.getTextMapPropagator().inject(Context.current(), carrier, setter)

              val requestWithTraceContext = request.headers(carrier.toMap)

              delegate
                .send(requestWithTraceContext)
                .map { response =>
                  span.setAllAttributes(config.responseAttributes(request, response))
                  response
                }
                .handleError { case e: Throwable =>
                  ResponseException.find(e) match {
                    case Some(re) =>
                      span.setAllAttributes(
                        config.responseAttributes(request, Response((), re.response.code, request.onlyMetadata))
                      )
                    case _ =>
                      span.setAllAttributes(config.errorAttributes(e))
                  }
                  monad.error(e)
                }
            } finally {
              scope.close()
            }
          }, {
            monad.eval(span.end())
          }
        )
      }
  }
}

object OpenTelemetryTracingBackend {
  def apply(delegate: SyncBackend, openTelemetry: OpenTelemetry): SyncBackend =
    apply(delegate, OpenTelemetryTracingConfig(openTelemetry))

  def apply[F[_]](delegate: Backend[F], openTelemetry: OpenTelemetry): Backend[F] =
    apply(delegate, OpenTelemetryTracingConfig(openTelemetry))

  def apply[F[_]](delegate: WebSocketBackend[F], openTelemetry: OpenTelemetry): WebSocketBackend[F] =
    apply(delegate, OpenTelemetryTracingConfig(openTelemetry))

  def apply[F[_], S](delegate: StreamBackend[F, S], openTelemetry: OpenTelemetry): StreamBackend[F, S] =
    apply(delegate, OpenTelemetryTracingConfig(openTelemetry))

  def apply[F[_], S](
      delegate: WebSocketStreamBackend[F, S],
      openTelemetry: OpenTelemetry
  ): WebSocketStreamBackend[F, S] =
    apply(delegate, OpenTelemetryTracingConfig(openTelemetry))

  def apply(delegate: WebSocketSyncBackend, openTelemetry: OpenTelemetry): WebSocketSyncBackend =
    apply(delegate, OpenTelemetryTracingConfig(openTelemetry))

  def apply(delegate: SyncBackend, config: OpenTelemetryTracingConfig): SyncBackend = {
    // redirects should be handled before tracing
    FollowRedirectsBackend(new OpenTelemetryTracingBackend(delegate, config) with SyncBackend)
  }

  def apply[F[_]](delegate: Backend[F], config: OpenTelemetryTracingConfig): Backend[F] = {
    FollowRedirectsBackend(new OpenTelemetryTracingBackend(delegate, config) with Backend[F])
  }

  def apply[F[_]](delegate: WebSocketBackend[F], config: OpenTelemetryTracingConfig): WebSocketBackend[F] = {
    FollowRedirectsBackend(new OpenTelemetryTracingBackend(delegate, config) with WebSocketBackend[F])
  }

  def apply[F[_], S](delegate: StreamBackend[F, S], config: OpenTelemetryTracingConfig): StreamBackend[F, S] = {
    FollowRedirectsBackend(new OpenTelemetryTracingBackend(delegate, config) with StreamBackend[F, S])
  }

  def apply[F[_], S](
      delegate: WebSocketStreamBackend[F, S],
      config: OpenTelemetryTracingConfig
  ): WebSocketStreamBackend[F, S] = {
    FollowRedirectsBackend(new OpenTelemetryTracingBackend(delegate, config) with WebSocketStreamBackend[F, S])
  }

  def apply(delegate: WebSocketSyncBackend, config: OpenTelemetryTracingConfig): WebSocketSyncBackend =
    FollowRedirectsBackend(new OpenTelemetryTracingBackend(delegate, config) with WebSocketSyncBackend)
}
