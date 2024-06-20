package sttp.client4.prometheus

import io.prometheus.metrics.core.datapoints.{GaugeDataPoint, Timer}
import io.prometheus.metrics.core.metrics.{Counter, Gauge, Histogram, Summary}
import io.prometheus.metrics.model.registry.{Collector, PrometheusRegistry}
import io.prometheus.metrics.model.snapshots.Unit.SECONDS
import sttp.client4.listener.{ListenerBackend, RequestListener}
import sttp.client4.prometheus.PrometheusBackend.RequestCollectors
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.client4.{wrappers, _}
import sttp.model.StatusCode
import sttp.shared.Identity

import java.util.concurrent.ConcurrentHashMap

object PrometheusBackend {
  /*
    Metrics names and model for Prometheus is based on these two specifications:
    https://prometheus.io/docs/practices/naming/
    https://prometheus.io/docs/concepts/data_model/#metric-names-and-labels
   * */
  val DefaultHistogramName = "http_client_request_duration_seconds"
  val DefaultRequestSizeName = "http_client_request_size_bytes"
  val DefaultResponseSizeName = "http_client_response_size_bytes"
  val DefaultRequestsActiveGaugeName = "http_client_requests_active"
  val DefaultSuccessCounterName = "http_client_requests_success"
  val DefaultErrorCounterName = "http_client_requests_error"
  val DefaultFailureCounterName = "http_client_requests_failure"

  val DefaultMethodLabel = "method"
  val DefaultStatusLabel = "status"

  def apply(delegate: SyncBackend): SyncBackend =
    apply(delegate, PrometheusConfig.Default)

  def apply[F[_]](delegate: Backend[F]): Backend[F] =
    apply(delegate, PrometheusConfig.Default)

  def apply[F[_]](delegate: WebSocketBackend[F]): WebSocketBackend[F] =
    apply(delegate, PrometheusConfig.Default)

  def apply[F[_], S](delegate: StreamBackend[F, S]): StreamBackend[F, S] =
    apply(delegate, PrometheusConfig.Default)

  def apply[F[_], S](delegate: WebSocketStreamBackend[F, S]): WebSocketStreamBackend[F, S] =
    apply(delegate, PrometheusConfig.Default)

  def apply(delegate: SyncBackend, config: PrometheusConfig): SyncBackend =
    // redirects should be handled before prometheus
    FollowRedirectsBackend(ListenerBackend(delegate, RequestListener.lift(listener(config), delegate.monad)))

  def apply[F[_]](delegate: Backend[F], config: PrometheusConfig): Backend[F] =
    wrappers.FollowRedirectsBackend[F](
      ListenerBackend(delegate, RequestListener.lift(listener(config), delegate.monad))
    )

  def apply[F[_]](delegate: WebSocketBackend[F], config: PrometheusConfig): WebSocketBackend[F] =
    wrappers.FollowRedirectsBackend(ListenerBackend(delegate, RequestListener.lift(listener(config), delegate.monad)))

  def apply[F[_], S](delegate: StreamBackend[F, S], config: PrometheusConfig): StreamBackend[F, S] =
    wrappers.FollowRedirectsBackend(ListenerBackend(delegate, RequestListener.lift(listener(config), delegate.monad)))

  def apply[F[_], S](delegate: WebSocketStreamBackend[F, S], config: PrometheusConfig): WebSocketStreamBackend[F, S] =
    wrappers.FollowRedirectsBackend(ListenerBackend(delegate, RequestListener.lift(listener(config), delegate.monad)))

  private def listener(config: PrometheusConfig): PrometheusListener =
    new PrometheusListener(
      (req: GenericRequest[_, _]) => config.requestToHistogramNameMapper(req),
      (req: GenericRequest[_, _]) => config.requestToInProgressGaugeNameMapper(req),
      (rr: (GenericRequest[_, _], Response[_])) => config.responseToSuccessCounterMapper(rr._1, rr._2),
      (rr: (GenericRequest[_, _], Response[_])) => config.responseToErrorCounterMapper(rr._1, rr._2),
      (r: (GenericRequest[_, _], Throwable)) => config.requestToFailureCounterMapper(r._1, r._2),
      (req: GenericRequest[_, _]) => config.requestToSizeSummaryMapper(req),
      (rr: (GenericRequest[_, _], Response[_])) => config.responseToSizeSummaryMapper(rr._1, rr._2),
      config.prometheusRegistry,
      cacheFor(histograms, config.prometheusRegistry),
      cacheFor(gauges, config.prometheusRegistry),
      cacheFor(counters, config.prometheusRegistry),
      cacheFor(summaries, config.prometheusRegistry)
    )

  /** Add, if not present, a "method" label. That is, if the user already supplied such a label, it is left as-is.
    *
    * @param config
    *   The collector config to which the label should be added.
    * @return
    *   The modified collector config. The config can be used when configuring the backend using [[apply]].
    */
  def addMethodLabel[T <: BaseCollectorConfig](config: T, req: GenericRequest[_, _]): config.T = {
    val methodLabel: Option[(String, String)] =
      if (config.labels.map(_._1.toLowerCase).contains(DefaultMethodLabel)) {
        None
      } else {
        Some((DefaultMethodLabel, req.method.method.toUpperCase))
      }

    config.addLabels(methodLabel.toList)
  }

  /** Add, if not present, a "status" label. That is, if the user already supplied such a label, it is left as-is.
    *
    * @param config
    *   The collector config to which the label should be added.
    * @return
    *   The modified collector config. The config can be used when configuring the backend using [[apply]].
    */
  def addStatusLabel[T <: BaseCollectorConfig](config: T, resp: Response[_]): config.T = {
    val statusLabel: Option[(String, String)] =
      if (config.labels.map(_._1.toLowerCase).contains(DefaultStatusLabel)) {
        None
      } else {
        Some((DefaultStatusLabel, mapStatusToLabelValue(resp.code)))
      }

    config.addLabels(statusLabel.toList)
  }

  private def mapStatusToLabelValue(s: StatusCode): String =
    if (s.isInformational) "1xx"
    else if (s.isSuccess) "2xx"
    else if (s.isRedirect) "3xx"
    else if (s.isClientError) "4xx"
    else if (s.isServerError) "5xx"
    else s.code.toString

  /** Clear cached collectors (gauges and histograms) both from the given collector registry, and from the backend.
    */
  def clear(prometheusRegistry: PrometheusRegistry): Unit = {
    clear(prometheusRegistry, histograms)
    clear(prometheusRegistry, gauges)
    clear(prometheusRegistry, counters)
    clear(prometheusRegistry, summaries)
  }

  /*
  Each collector can be registered in a collector registry only once - however there might be multiple backends registered
  with the same collector (trying to register a collector under the same name twice results in an exception).
  Hence, we need to store a global cache o created histograms/gauges, so that we can properly re-use them.
   */

  private def clear[T <: Collector](
      prometheusRegistry: PrometheusRegistry,
      collectors: ConcurrentHashMap[PrometheusRegistry, ConcurrentHashMap[String, T]]
  ): Unit = {
    collectors
      .getOrDefault(prometheusRegistry, new ConcurrentHashMap[String, T]())
      .values
      .forEach(c => prometheusRegistry.unregister(c))
    collectors.remove(prometheusRegistry)
  }

  private val histograms = new ConcurrentHashMap[PrometheusRegistry, ConcurrentHashMap[String, Histogram]]
  private val gauges = new ConcurrentHashMap[PrometheusRegistry, ConcurrentHashMap[String, Gauge]]
  private val counters = new ConcurrentHashMap[PrometheusRegistry, ConcurrentHashMap[String, Counter]]
  private val summaries = new ConcurrentHashMap[PrometheusRegistry, ConcurrentHashMap[String, Summary]]

  private def cacheFor[T](
      cache: ConcurrentHashMap[PrometheusRegistry, ConcurrentHashMap[String, T]],
      prometheusRegistry: PrometheusRegistry
  ): ConcurrentHashMap[String, T] =
    cache.computeIfAbsent(
      prometheusRegistry,
      new java.util.function.Function[PrometheusRegistry, ConcurrentHashMap[String, T]] {
        override def apply(t: PrometheusRegistry): ConcurrentHashMap[String, T] = new ConcurrentHashMap[String, T]()
      }
    )

  final case class RequestCollectors(maybeTimer: Option[Timer], maybeGauge: Option[GaugeDataPoint])
}

class PrometheusListener(
    requestToHistogramNameMapper: GenericRequest[_, _] => Option[HistogramCollectorConfig],
    requestToInProgressGaugeNameMapper: GenericRequest[_, _] => Option[CollectorConfig],
    requestToSuccessCounterMapper: ((GenericRequest[_, _], Response[_])) => Option[CollectorConfig],
    requestToErrorCounterMapper: ((GenericRequest[_, _], Response[_])) => Option[CollectorConfig],
    requestToFailureCounterMapper: ((GenericRequest[_, _], Exception)) => Option[CollectorConfig],
    requestToSizeSummaryMapper: GenericRequest[_, _] => Option[CollectorConfig],
    responseToSizeSummaryMapper: ((GenericRequest[_, _], Response[_])) => Option[CollectorConfig],
    prometheusRegistry: PrometheusRegistry,
    histogramsCache: ConcurrentHashMap[String, Histogram],
    gaugesCache: ConcurrentHashMap[String, Gauge],
    countersCache: ConcurrentHashMap[String, Counter],
    summariesCache: ConcurrentHashMap[String, Summary]
) extends RequestListener[Identity, RequestCollectors] {

  override def beforeRequest(request: GenericRequest[_, _]): RequestCollectors = {
    val requestTimer: Option[Timer] = for {
      histogramData <- requestToHistogramNameMapper(request)
      histogram: Histogram = getOrCreateMetric(histogramsCache, histogramData, createNewHistogram)
    } yield histogram.labelValues(histogramData.labelValues: _*).startTimer()

    val gauge: Option[GaugeDataPoint] = for {
      gaugeData <- requestToInProgressGaugeNameMapper(request)
    } yield getOrCreateMetric(gaugesCache, gaugeData, createNewGauge).labelValues(gaugeData.labelValues: _*)

    observeRequestContentLengthSummaryIfMapped(request, requestToSizeSummaryMapper)

    gauge.foreach(_.inc())

    RequestCollectors(requestTimer, gauge)
  }

  override def requestException(
      request: GenericRequest[_, _],
      requestCollectors: RequestCollectors,
      e: Exception
  ): Unit =
    HttpError.find(e) match {
      case Some(HttpError(body, statusCode)) =>
        requestSuccessful(request, Response(body, statusCode, request.onlyMetadata), requestCollectors)
      case _ =>
        requestCollectors.maybeTimer.foreach(_.observeDuration())
        requestCollectors.maybeGauge.foreach(_.dec())
        incCounterIfMapped((request, e), requestToFailureCounterMapper)
    }

  override def requestSuccessful(
      request: GenericRequest[_, _],
      response: Response[_],
      requestCollectors: RequestCollectors
  ): Unit = {
    requestCollectors.maybeTimer.foreach(_.observeDuration())
    requestCollectors.maybeGauge.foreach(_.dec())
    observeResponseContentLengthSummaryIfMapped(request, response, responseToSizeSummaryMapper)

    if (response.isSuccess) {
      incCounterIfMapped((request, response), requestToSuccessCounterMapper)
    } else {
      incCounterIfMapped((request, response), requestToErrorCounterMapper)
    }
  }

  private def incCounterIfMapped[T](
      request: T,
      mapper: T => Option[BaseCollectorConfig]
  ): Unit =
    mapper(request).foreach { data =>
      getOrCreateMetric(countersCache, data, createNewCounter).labelValues(data.labelValues: _*).inc()
    }

  private def observeResponseContentLengthSummaryIfMapped(
      request: GenericRequest[_, _],
      response: Response[_],
      mapper: ((GenericRequest[_, _], Response[_])) => Option[BaseCollectorConfig]
  ): Unit =
    mapper((request, response)).foreach { data =>
      response.contentLength.map(_.toDouble).foreach { size =>
        getOrCreateMetric(summariesCache, data, createNewSummary).labelValues(data.labelValues: _*).observe(size)
      }
    }

  private def observeRequestContentLengthSummaryIfMapped(
      request: GenericRequest[_, _],
      mapper: GenericRequest[_, _] => Option[BaseCollectorConfig]
  ): Unit =
    mapper(request).foreach { data =>
      (request.contentLength: Option[Long]).map(_.toDouble).foreach { size =>
        getOrCreateMetric(summariesCache, data, createNewSummary).labelValues(data.labelValues: _*).observe(size)
      }
    }

  private def getOrCreateMetric[T, C <: BaseCollectorConfig](
      cache: ConcurrentHashMap[String, T],
      data: C,
      create: C => T
  ): T =
    cache.computeIfAbsent(
      data.collectorName,
      new java.util.function.Function[String, T] {
        override def apply(t: String): T = create(data)
      }
    )

  private def createNewHistogram(data: HistogramCollectorConfig): Histogram =
    Histogram
      .builder()
      .unit(data.unit)
      .classicUpperBounds(data.buckets: _*)
      .name(data.collectorName)
      .labelNames(data.labelNames: _*)
      .help(data.help)
      .register(prometheusRegistry)

  private def createNewGauge(data: BaseCollectorConfig): Gauge =
    Gauge
      .builder()
      .name(data.collectorName)
      .labelNames(data.labelNames: _*)
      .help(data.help)
      .register(prometheusRegistry)

  private def createNewCounter(data: BaseCollectorConfig): Counter =
    Counter
      .builder()
      .name(data.collectorName)
      .labelNames(data.labelNames: _*)
      .help(data.help)
      .register(prometheusRegistry)

  private def createNewSummary(data: BaseCollectorConfig): Summary =
    Summary
      .builder()
      .name(data.collectorName)
      .labelNames(data.labelNames: _*)
      .help(data.help)
      .register(prometheusRegistry)
}

trait BaseCollectorConfig {
  type T <: BaseCollectorConfig

  def collectorName: String
  def labels: List[(String, String)]
  def help: String

  def addLabels(lbs: List[(String, String)]): T

  def labelNames: Seq[String] = labels.map(_._1)
  def labelValues: Seq[String] = labels.map(_._2)
}

/** Represents the name of a collector, together with label names and values. The same labels must be always returned,
  * and in the same order.
  */
case class CollectorConfig(
    collectorName: String,
    description: Option[String] = None,
    labels: List[(String, String)] = Nil
) extends BaseCollectorConfig {
  override type T = CollectorConfig
  override def addLabels(lbs: List[(String, String)]): CollectorConfig = copy(labels = labels ++ lbs)
  override def help: String = description.getOrElse(collectorName)
}

/** Represents the name of a collector with configurable histogram buckets. */
case class HistogramCollectorConfig(
    collectorName: String,
    description: Option[String] = None,
    unit: io.prometheus.metrics.model.snapshots.Unit = SECONDS,
    labels: List[(String, String)] = Nil,
    buckets: List[Double] = HistogramCollectorConfig.DefaultBuckets
) extends BaseCollectorConfig {
  override type T = HistogramCollectorConfig
  override def addLabels(lbs: List[(String, String)]): HistogramCollectorConfig = copy(labels = labels ++ lbs)
  override def help: String = description.getOrElse(collectorName)
}

object HistogramCollectorConfig {
  val DefaultBuckets: List[Double] = List(.005, .01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10)
}
