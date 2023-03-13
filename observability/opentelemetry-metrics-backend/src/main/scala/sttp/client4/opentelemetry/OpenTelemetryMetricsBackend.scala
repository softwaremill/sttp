package sttp.client4.opentelemetry

import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.{DoubleHistogram, LongCounter, LongUpDownCounter, Meter}
import io.opentelemetry.api.OpenTelemetry
import sttp.client4._
import sttp.client4.listener.{ListenerBackend, RequestListener}

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

object OpenTelemetryMetricsBackend {
  val DefaultLatencyHistogramName = "sttp_request_latency"
  val DefaultRequestsInProgressCounterName = "sttp_requests_in_progress"
  val DefaultSuccessCounterName = "sttp_requests_success_count"
  val DefaultErrorCounterName = "sttp_requests_error_count"
  val DefaultFailureCounterName = "sttp_requests_failure_count"
  val DefaultRequestSizeHistogramName = "sttp_request_size_bytes"
  val DefaultResponseSizeHistogramName = "sttp_response_size_bytes"

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
    val listener = OpenTelemetryMetricsListener(config)
    // redirects should be handled before metrics
    FollowRedirectsBackend(ListenerBackend(delegate, RequestListener.lift(listener, delegate.monad)))
  }

  def apply[F[_]](delegate: Backend[F], config: OpenTelemetryMetricsConfig): Backend[F] = {
    val listener = OpenTelemetryMetricsListener(config)
    // redirects should be handled before metrics
    FollowRedirectsBackend(ListenerBackend(delegate, RequestListener.lift(listener, delegate.monad)))
  }

  def apply[F[_]](delegate: WebSocketBackend[F], config: OpenTelemetryMetricsConfig): WebSocketBackend[F] = {
    val listener = OpenTelemetryMetricsListener(config)
    FollowRedirectsBackend(ListenerBackend(delegate, RequestListener.lift(listener, delegate.monad)))
  }

  def apply[F[_], S](delegate: StreamBackend[F, S], config: OpenTelemetryMetricsConfig): StreamBackend[F, S] = {
    val listener = OpenTelemetryMetricsListener(config)
    FollowRedirectsBackend(
      ListenerBackend(delegate, RequestListener.lift(listener, delegate.monad))
    )
  }

  def apply[F[_], S](
      delegate: WebSocketStreamBackend[F, S],
      config: OpenTelemetryMetricsConfig
  ): WebSocketStreamBackend[F, S] = {
    val listener = OpenTelemetryMetricsListener(config)
    FollowRedirectsBackend(ListenerBackend(delegate, RequestListener.lift(listener, delegate.monad)))
  }
}

private object OpenTelemetryMetricsListener {
  def apply(config: OpenTelemetryMetricsConfig): OpenTelemetryMetricsListener =
    new OpenTelemetryMetricsListener(
      config.meter,
      config.clock,
      config.requestToLatencyHistogramMapper,
      config.requestToInProgressCounterMapper,
      config.responseToSuccessCounterMapper,
      config.requestToErrorCounterMapper,
      config.requestToFailureCounterMapper,
      config.requestToSizeHistogramMapper,
      config.responseToSizeHistogramMapper
    )
}

private class OpenTelemetryMetricsListener(
                                            meter: Meter,
                                            clock: Clock,
                                            requestToLatencyHistogramMapper: GenericRequest[_, _] => Option[CollectorConfig],
                                            requestToInProgressCounterMapper: GenericRequest[_, _] => Option[CollectorConfig],
                                            responseToSuccessCounterMapper: Response[_] => Option[CollectorConfig],
                                            requestToErrorCounterMapper: Response[_] => Option[CollectorConfig],
                                            requestToFailureCounterMapper: (GenericRequest[_, _], Throwable) => Option[CollectorConfig],
                                            requestToSizeHistogramMapper: GenericRequest[_, _] => Option[CollectorConfig],
                                            responseToSizeHistogramMapper: Response[_] => Option[CollectorConfig]
) extends RequestListener[Identity, Option[Long]] {

  private val counters = new ConcurrentHashMap[String, LongCounter]
  private val histograms = new ConcurrentHashMap[String, DoubleHistogram]()
  private val upAndDownCounter = new ConcurrentHashMap[String, LongUpDownCounter]()

  override def beforeRequest(request: GenericRequest[_, _]): Option[Long] = {
    updateInProgressCounter(request, 1)
    recordHistogram(requestToSizeHistogramMapper(request), request.contentLength)
    requestToLatencyHistogramMapper(request).map(_ => clock.millis())
  }

  override def requestSuccessful(request: GenericRequest[_, _], response: Response[_], tag: Option[Long]): Unit = {
    if (response.isSuccess) {
      incrementCounter(responseToSuccessCounterMapper(response))
    } else {
      incrementCounter(requestToErrorCounterMapper(response))
    }
    recordHistogram(responseToSizeHistogramMapper(response), response.contentLength)
    recordHistogram(requestToLatencyHistogramMapper(request), tag.map(clock.millis() - _))
    updateInProgressCounter(request, -1)
  }

  override def requestException(request: GenericRequest[_, _], tag: Option[Long], e: Exception): Unit = {
    HttpError.find(e) match {
      case Some(HttpError(body, statusCode)) =>
        requestSuccessful(request, Response(body, statusCode).copy(request = request.onlyMetadata), tag)
      case _ =>
        incrementCounter(requestToFailureCounterMapper(request, e))
        recordHistogram(requestToLatencyHistogramMapper(request), tag.map(clock.millis() - _))
        updateInProgressCounter(request, -1)
    }
  }

  private def updateInProgressCounter[R, T](request: GenericRequest[T, R], delta: Long): Unit = {
    requestToInProgressCounterMapper(request)
      .foreach(config =>
        getOrCreateMetric(upAndDownCounter, config, createNewUpDownCounter).add(delta, config.attributes)
      )
  }

  private def recordHistogram(config: Option[CollectorConfig], size: Option[Long]): Unit = config.foreach { cfg =>
    getOrCreateMetric(histograms, cfg, createNewHistogram).record(size.getOrElse(0L).toDouble, cfg.attributes)
  }

  private def incrementCounter(collectorConfig: Option[CollectorConfig]): Unit = {
    collectorConfig
      .foreach(config => getOrCreateMetric(counters, config, createNewCounter).add(1, config.attributes))
  }

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

  private def createNewUpDownCounter(collectorConfig: CollectorConfig): LongUpDownCounter = {
    var b = meter.upDownCounterBuilder(collectorConfig.name)
    b = collectorConfig.unit.fold(b)(b.setUnit)
    b = collectorConfig.description.fold(b)(b.setDescription)
    b.build()
  }

  private def createNewCounter(collectorConfig: CollectorConfig): LongCounter = {
    var b = meter.counterBuilder(collectorConfig.name)
    b = collectorConfig.unit.fold(b)(b.setUnit)
    b = collectorConfig.description.fold(b)(b.setDescription)
    b.build()
  }

  private def createNewHistogram(collectorConfig: CollectorConfig): DoubleHistogram = {
    var b = meter.histogramBuilder(collectorConfig.name)
    b = collectorConfig.unit.fold(b)(b.setUnit)
    b = collectorConfig.description.fold(b)(b.setDescription)
    b.build()
  }
}

case class CollectorConfig(
    name: String,
    description: Option[String] = None,
    unit: Option[String] = None,
    attributes: Attributes = Attributes.empty()
)
object CollectorConfig {
  val Bytes = "b"
  val Milliseconds = "ms"
}

case class MeterConfig(name: String, version: String)
object MeterConfig {
  val Default: MeterConfig = MeterConfig("sttp-client4", "1.0.0")
}
