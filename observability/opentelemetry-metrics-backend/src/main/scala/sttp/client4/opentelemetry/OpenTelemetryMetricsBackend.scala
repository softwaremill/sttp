package sttp.client4.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.{AttributeKey, Attributes}
import io.opentelemetry.api.metrics.{DoubleHistogram, LongCounter, LongUpDownCounter, Meter}
import sttp.client4.listener.{ListenerBackend, RequestListener}
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.client4._
import sttp.shared.Identity

import java.time.Clock
import java.util.concurrent.ConcurrentHashMap

object OpenTelemetryMetricsBackend {
  /*
    Metrics names and model for Open Telemetry is based on these two specifications:
    https://opentelemetry.io/docs/specs/semconv/http/http-metrics/#http-client
    https://github.com/open-telemetry/opentelemetry-specification/blob/v1.31.0/specification/metrics/api.md#instrument
   * */
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
    val attributes = createRequestAttributes(request)

    updateInProgressCounter(request, 1, attributes)
    recordHistogram(requestToSizeHistogramMapper(request), request.contentLength, attributes)
    requestToLatencyHistogramMapper(request).map { _ =>
      val timestamp = clock.millis()
      timestamp
    }
  }

  override def requestSuccessful(request: GenericRequest[_, _], response: Response[_], tag: Option[Long]): Unit = {
    val requestAttributes = createRequestAttributes(request)
    val responseAttributes = createResponseAttributes(response)

    val combinedAttributes = requestAttributes.toBuilder().putAll(responseAttributes).build()

    if (response.isSuccess) {
      incrementCounter(responseToSuccessCounterMapper(response), combinedAttributes)
    } else {
      incrementCounter(requestToErrorCounterMapper(response), combinedAttributes)
    }

    recordHistogram(responseToSizeHistogramMapper(response), response.contentLength, combinedAttributes)
    recordHistogram(requestToLatencyHistogramMapper(request), tag.map(clock.millis() - _), combinedAttributes)
    updateInProgressCounter(request, -1, requestAttributes)
  }

  override def requestException(request: GenericRequest[_, _], tag: Option[Long], e: Exception): Unit = {
    val requestAttributes = createRequestAttributes(request)
    val errorAttributes = createErrorAttributes(e)

    HttpError.find(e) match {
      case Some(HttpError(body, statusCode)) =>
        requestSuccessful(request, Response(body, statusCode, request.onlyMetadata), tag)
      case _ =>
        incrementCounter(requestToFailureCounterMapper(request, e), errorAttributes)
        recordHistogram(requestToLatencyHistogramMapper(request), tag.map(clock.millis() - _), errorAttributes)
        updateInProgressCounter(request, -1, requestAttributes)
    }
  }

  private def updateInProgressCounter[R, T](request: GenericRequest[T, R], delta: Long, attributes: Attributes): Unit =
    requestToInProgressCounterMapper(request)
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

  private def createRequestAttributes(request: GenericRequest[_, _]): Attributes = {
    val attributes = Attributes
      .builder()
      .put(AttributeKey.stringKey("http.request.method"), request.method.method)
      .put(AttributeKey.stringKey("server.address"), request.uri.host.getOrElse("unknown"))
      .put(AttributeKey.longKey("server.port").asInstanceOf[AttributeKey[Any]], request.uri.port.getOrElse(80).toLong)
      .build()

    attributes
  }

  private def createResponseAttributes(response: Response[_]): Attributes =
    Attributes
      .builder()
      .put(AttributeKey.longKey("http.response.status_code").asInstanceOf[AttributeKey[Any]], response.code.code.toLong)
      .build()

  private def createErrorAttributes(e: Throwable): Attributes = {
    val errorType = e match {
      case _: java.net.UnknownHostException => "unknown_host"
      case _                                => e.getClass.getSimpleName
    }
    Attributes.builder().put("error.type", errorType).build()
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
