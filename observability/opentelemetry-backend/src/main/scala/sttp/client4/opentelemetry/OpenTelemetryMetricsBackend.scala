package sttp.client4.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.DoubleHistogram
import io.opentelemetry.api.metrics.LongCounter
import io.opentelemetry.api.metrics.LongUpDownCounter
import sttp.client4._
import sttp.client4.listener.ListenerBackend
import sttp.client4.listener.RequestListener
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.model.ResponseMetadata
import sttp.shared.Identity

import java.util.concurrent.ConcurrentHashMap

object OpenTelemetryMetricsBackend {
  // Metrics names and model for Open Telemetry is based on these two specifications:
  // https://opentelemetry.io/docs/specs/semconv/http/http-metrics/#http-client
  // https://github.com/open-telemetry/opentelemetry-specification/blob/v1.31.0/specification/metrics/api.md#instrument

  val DefaultLatencyHistogramName = "http.client.request.duration"
  val DefaultRequestSizeHistogramName = "http.client.request.size.bytes"
  val DefaultResponseSizeHistogramName = "http.client.response.size.bytes"
  val DefaultRequestsActiveCounterName = "http.client.requests.active"
  val DefaultSuccessCounterName = "http.client.requests.success"
  val DefaultErrorCounterName = "http.client.requests.error"
  val DefaultFailureCounterName = "http.client.requests.failure"

  def apply(delegate: SyncBackend, openTelemetry: OpenTelemetry): SyncBackend =
    apply(delegate, OpenTelemetryMetricsConfig(openTelemetry))

  def apply[F[_]](delegate: Backend[F], openTelemetry: OpenTelemetry): Backend[F] =
    apply(delegate, OpenTelemetryMetricsConfig(openTelemetry))

  def apply[F[_]](delegate: WebSocketBackend[F], openTelemetry: OpenTelemetry): WebSocketBackend[F] =
    apply(delegate, OpenTelemetryMetricsConfig(openTelemetry))

  def apply[F[_], S](delegate: StreamBackend[F, S], openTelemetry: OpenTelemetry): StreamBackend[F, S] =
    apply(delegate, OpenTelemetryMetricsConfig(openTelemetry))

  def apply[F[_], S](
      delegate: WebSocketStreamBackend[F, S],
      openTelemetry: OpenTelemetry
  ): WebSocketStreamBackend[F, S] =
    apply(delegate, OpenTelemetryMetricsConfig(openTelemetry))

  def apply(delegate: SyncBackend, config: OpenTelemetryMetricsConfig): SyncBackend = {
    val listener = new OpenTelemetryMetricsListener(config)
    // redirects should be handled before metrics
    FollowRedirectsBackend(ListenerBackend(delegate, RequestListener.lift(listener, delegate.monad)))
  }

  def apply[F[_]](delegate: Backend[F], config: OpenTelemetryMetricsConfig): Backend[F] = {
    val listener = new OpenTelemetryMetricsListener(config)
    // redirects should be handled before metrics
    FollowRedirectsBackend(ListenerBackend(delegate, RequestListener.lift(listener, delegate.monad)))
  }

  def apply[F[_]](delegate: WebSocketBackend[F], config: OpenTelemetryMetricsConfig): WebSocketBackend[F] = {
    val listener = new OpenTelemetryMetricsListener(config)
    FollowRedirectsBackend(ListenerBackend(delegate, RequestListener.lift(listener, delegate.monad)))
  }

  def apply[F[_], S](delegate: StreamBackend[F, S], config: OpenTelemetryMetricsConfig): StreamBackend[F, S] = {
    val listener = new OpenTelemetryMetricsListener(config)
    FollowRedirectsBackend(
      ListenerBackend(delegate, RequestListener.lift(listener, delegate.monad))
    )
  }

  def apply[F[_], S](
      delegate: WebSocketStreamBackend[F, S],
      config: OpenTelemetryMetricsConfig
  ): WebSocketStreamBackend[F, S] = {
    val listener = new OpenTelemetryMetricsListener(config)
    FollowRedirectsBackend(ListenerBackend(delegate, RequestListener.lift(listener, delegate.monad)))
  }
}

private class OpenTelemetryMetricsListener(config: OpenTelemetryMetricsConfig)
    extends RequestListener[Identity, Option[Long]] {

  private val counters = new ConcurrentHashMap[String, LongCounter]
  private val histograms = new ConcurrentHashMap[String, DoubleHistogram]()
  private val upAndDownCounter = new ConcurrentHashMap[String, LongUpDownCounter]()

  override def before[T, R](request: GenericRequest[T, R]): Option[Long] = {
    val attributes = config.requestAttributes(request)

    updateInProgressCounter(request, 1, attributes)
    recordHistogram(config.requestToSizeHistogramMapper(request), request.contentLength, attributes)
    config.requestToLatencyHistogramMapper(request).map { _ => config.clock.millis() }
  }

  override def responseBodyReceived(
      request: GenericRequest[_, _],
      response: ResponseMetadata,
      tag: Option[Long]
  ): Unit = captureResponseMetrics(request, response, tag)

  override def responseHandled(
      request: GenericRequest[_, _],
      response: ResponseMetadata,
      tag: Option[Long],
      e: Option[ResponseException[_]]
  ): Unit = {
    // responseBodyReceived is not called for WebSocket requests
    // ignoring the tag as there's no point in capturing timing information for WebSockets
    if (request.isWebSocket) captureResponseMetrics(request, response, None)
  }

  private def captureResponseMetrics(
      request: GenericRequest[_, _],
      response: ResponseMetadata,
      tag: Option[Long]
  ): Unit = {
    val requestAttributes = config.requestAttributes(request)
    val responseAttributes = config.responseAttributes(request, response)

    val combinedAttributes = requestAttributes.toBuilder().putAll(responseAttributes).build()

    if (response.isSuccess) {
      incrementCounter(config.responseToSuccessCounterMapper(request, response), combinedAttributes)
    } else {
      incrementCounter(config.requestToErrorCounterMapper(request, response), combinedAttributes)
    }

    recordHistogram(config.responseToSizeHistogramMapper(request, response), response.contentLength, combinedAttributes)
    recordHistogram(
      config.requestToLatencyHistogramMapper(request),
      tag.map(config.clock.millis() - _),
      combinedAttributes
    )
    updateInProgressCounter(request, -1, requestAttributes)
  }

  override def exception(
      request: GenericRequest[_, _],
      tag: Option[Long],
      e: Throwable,
      responseBodyReceivedCalled: Boolean
  ): Unit = {
    if (!responseBodyReceivedCalled) {
      val requestAttributes = config.requestAttributes(request)
      val errorAttributes = config.errorAttributes(e)

      incrementCounter(config.requestToFailureCounterMapper(request, e), errorAttributes)
      recordHistogram(
        config.requestToLatencyHistogramMapper(request),
        tag.map(config.clock.millis() - _),
        errorAttributes
      )
      updateInProgressCounter(request, -1, requestAttributes)
    }
  }

  private def updateInProgressCounter[R, T](request: GenericRequest[T, R], delta: Long, attributes: Attributes): Unit =
    config
      .requestToInProgressCounterMapper(request)
      .foreach(config => getOrCreateMetric(upAndDownCounter, config, createNewUpDownCounter).add(delta, attributes))

  private def recordHistogram(
      config: Option[HistogramCollectorConfig],
      size: Option[Long],
      attributes: Attributes
  ): Unit = config.foreach { cfg =>
    getOrCreateHistogram(histograms, cfg, createNewHistogram).record(size.getOrElse(0L).toDouble, attributes)
  }

  private def incrementCounter(collectorConfig: Option[CollectorConfig], attributes: Attributes): Unit =
    collectorConfig
      .foreach(config => getOrCreateMetric(counters, config, createNewCounter).add(1, attributes))

  private def getOrCreateMetric[T](
      cache: ConcurrentHashMap[String, T],
      data: CollectorConfig,
      create: CollectorConfig => T
  ): T =
    cache.computeIfAbsent(
      data.name,
      new java.util.function.Function[String, T] {
        override def apply(t: String): T = create(data)
      }
    )

  private def getOrCreateHistogram[T](
      cache: ConcurrentHashMap[String, T],
      data: HistogramCollectorConfig,
      create: HistogramCollectorConfig => T
  ): T =
    cache.computeIfAbsent(
      data.name,
      new java.util.function.Function[String, T] {
        override def apply(t: String): T = create(data)
      }
    )

  private def createNewUpDownCounter(collectorConfig: CollectorConfig): LongUpDownCounter = {
    var b = config.meter.upDownCounterBuilder(collectorConfig.name)
    b = collectorConfig.unit.fold(b)(b.setUnit)
    b = collectorConfig.description.fold(b)(b.setDescription)
    b.build()
  }

  private def createNewCounter(collectorConfig: CollectorConfig): LongCounter = {
    var b = config.meter.counterBuilder(collectorConfig.name)
    b = collectorConfig.unit.fold(b)(b.setUnit)
    b = collectorConfig.description.fold(b)(b.setDescription)
    b.build()
  }

  private def createNewHistogram(histogramConfig: HistogramCollectorConfig): DoubleHistogram = {
    var b = config.meter
      .histogramBuilder(histogramConfig.name)
      .setExplicitBucketBoundariesAdvice(histogramConfig.buckets)
      .setUnit(histogramConfig.unit)
    b = histogramConfig.description.fold(b)(b.setDescription)
    b.build()
  }
}

case class CollectorConfig(
    name: String,
    description: Option[String] = None,
    unit: Option[String] = None,
    attributes: Attributes = Attributes.empty()
)

case class HistogramCollectorConfig(
    name: String,
    unit: String,
    description: Option[String] = None,
    attributes: Attributes = Attributes.empty(),
    buckets: java.util.List[java.lang.Double]
)

object HistogramCollectorConfig {
  val Bytes = "By"
  val Milliseconds = "ms"
  val DefaultLatencyBuckets: java.util.List[java.lang.Double] =
    java.util.List.of(.005, .01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10)
  // Should go as follows 100 bytes, 1Kb, 10Kb, 100kB, 1 Mb, 10 Mb, 100Mb, 100Mb +
  val DefaultSizeBuckets: java.util.List[java.lang.Double] =
    java.util.List.of(100, 1024, 10240, 102400, 1048576, 10485760, 104857600)
}
