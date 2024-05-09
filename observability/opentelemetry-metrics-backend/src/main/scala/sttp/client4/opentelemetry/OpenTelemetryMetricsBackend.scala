package sttp.client4.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.{DoubleHistogram, LongCounter, LongUpDownCounter, Meter}
import sttp.client4.listener.{ListenerBackend, RequestListener}
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.client4._

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

object OpenTelemetryMetricsBackend {
  val DefaultLatencyHistogramName = "sttp.request.duration"
  val DefaultRequestSizeHistogramName = "sttp.request.size.bytes"
  val DefaultResponseSizeHistogramName = "sttp.response.size.bytes"
  val DefaultRequestsInProgressCounterName = "sttp.requests.active"
  val DefaultSuccessCounterName = "sttp.requests.success"
  val DefaultErrorCounterName = "sttp.requests.error"
  val DefaultFailureCounterName = "sttp.requests.failure"

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
    wrappers.FollowRedirectsBackend(ListenerBackend(delegate, RequestListener.lift(listener, delegate.monad)))
  }

  def apply[F[_]](delegate: WebSocketBackend[F], config: OpenTelemetryMetricsConfig): WebSocketBackend[F] = {
    val listener = OpenTelemetryMetricsListener(config)
    wrappers.FollowRedirectsBackend(ListenerBackend(delegate, RequestListener.lift(listener, delegate.monad)))
  }

  def apply[F[_], S](delegate: StreamBackend[F, S], config: OpenTelemetryMetricsConfig): StreamBackend[F, S] = {
    val listener = OpenTelemetryMetricsListener(config)
    wrappers.FollowRedirectsBackend(
      ListenerBackend(delegate, RequestListener.lift(listener, delegate.monad))
    )
  }

  def apply[F[_], S](
      delegate: WebSocketStreamBackend[F, S],
      config: OpenTelemetryMetricsConfig
  ): WebSocketStreamBackend[F, S] = {
    val listener = OpenTelemetryMetricsListener(config)
    wrappers.FollowRedirectsBackend(ListenerBackend(delegate, RequestListener.lift(listener, delegate.monad)))
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
    requestToLatencyHistogramMapper: GenericRequest[_, _] => Option[HistogramCollectorConfig],
    requestToInProgressCounterMapper: GenericRequest[_, _] => Option[CollectorConfig],
    responseToSuccessCounterMapper: Response[_] => Option[CollectorConfig],
    requestToErrorCounterMapper: Response[_] => Option[CollectorConfig],
    requestToFailureCounterMapper: (GenericRequest[_, _], Throwable) => Option[CollectorConfig],
    requestToSizeHistogramMapper: GenericRequest[_, _] => Option[HistogramCollectorConfig],
    responseToSizeHistogramMapper: Response[_] => Option[HistogramCollectorConfig]
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

  override def requestException(request: GenericRequest[_, _], tag: Option[Long], e: Exception): Unit =
    HttpError.find(e) match {
      case Some(HttpError(body, statusCode)) =>
        requestSuccessful(request, Response(body, statusCode, request.onlyMetadata), tag)
      case _ =>
        incrementCounter(requestToFailureCounterMapper(request, e))
        recordHistogram(requestToLatencyHistogramMapper(request), tag.map(clock.millis() - _))
        updateInProgressCounter(request, -1)
    }

  private def updateInProgressCounter[R, T](request: GenericRequest[T, R], delta: Long): Unit =
    requestToInProgressCounterMapper(request)
      .foreach(config =>
        getOrCreateMetric(upAndDownCounter, config, createNewUpDownCounter).add(delta, config.attributes)
      )

  private def recordHistogram(config: Option[HistogramCollectorConfig], size: Option[Long]): Unit = config.foreach {
    cfg =>
      getOrCreateHistogram(histograms, cfg, createNewHistogram).record(size.getOrElse(0L).toDouble, cfg.attributes)
  }

  private def incrementCounter(collectorConfig: Option[CollectorConfig]): Unit =
    collectorConfig
      .foreach(config => getOrCreateMetric(counters, config, createNewCounter).add(1, config.attributes))

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

  private def createNewHistogram(config: HistogramCollectorConfig): DoubleHistogram = {
    var b = meter
      .histogramBuilder(config.name)
      .setExplicitBucketBoundariesAdvice(config.buckets)
      .setUnit(config.unit)
    b = config.description.fold(b)(b.setDescription)
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

case class MeterConfig(name: String, version: String)
object MeterConfig {
  val Default: MeterConfig = MeterConfig("sttp-client4", "1.0.0")
}
