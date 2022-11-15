package sttp.client3.prometheus

import java.util.concurrent.ConcurrentHashMap
import sttp.client3.{FollowRedirectsBackend, HttpError, Identity, Request, Response, SttpBackend}
import io.prometheus.client.{CollectorRegistry, Counter, Gauge, Histogram, Summary}
import sttp.client3.listener.{ListenerBackend, RequestListener}
import sttp.client3.prometheus.PrometheusBackend.RequestCollectors

import scala.collection.mutable

object PrometheusBackend {
  val DefaultHistogramName = "sttp_request_latency"
  val DefaultRequestsInProgressGaugeName = "sttp_requests_in_progress"
  val DefaultSuccessCounterName = "sttp_requests_success_count"
  val DefaultErrorCounterName = "sttp_requests_error_count"
  val DefaultFailureCounterName = "sttp_requests_failure_count"
  val DefaultRequestSizeName = "sttp_request_size_bytes"
  val DefaultResponseSizeName = "sttp_response_size_bytes"

  def apply[F[_], P](
      delegate: SttpBackend[F, P],
      requestToHistogramNameMapper: Request[_, _] => Option[HistogramCollectorConfig] = (_: Request[_, _]) =>
        Some(HistogramCollectorConfig(DefaultHistogramName)),
      requestToInProgressGaugeNameMapper: Request[_, _] => Option[CollectorConfig] = (_: Request[_, _]) =>
        Some(CollectorConfig(DefaultRequestsInProgressGaugeName)),
      responseToSuccessCounterMapper: (Request[_, _], Response[_]) => Option[CollectorConfig] =
        (_: Request[_, _], _: Response[_]) => Some(CollectorConfig(DefaultSuccessCounterName)),
      responseToErrorCounterMapper: (Request[_, _], Response[_]) => Option[CollectorConfig] =
        (_: Request[_, _], _: Response[_]) => Some(CollectorConfig(DefaultErrorCounterName)),
      requestToFailureCounterMapper: (Request[_, _], Throwable) => Option[CollectorConfig] =
        (_: Request[_, _], _: Throwable) => Some(CollectorConfig(DefaultFailureCounterName)),
      requestToSizeSummaryMapper: Request[_, _] => Option[CollectorConfig] = (_: Request[_, _]) =>
        Some(CollectorConfig(DefaultRequestSizeName)),
      responseToSizeSummaryMapper: (Request[_, _], Response[_]) => Option[CollectorConfig] =
        (_: Request[_, _], _: Response[_]) => Some(CollectorConfig(DefaultResponseSizeName)),
      collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
  ): SttpBackend[F, P] = {
    // redirects should be handled before prometheus
    new FollowRedirectsBackend[F, P](
      new ListenerBackend[F, P, RequestCollectors](
        delegate,
        RequestListener.lift(
          new PrometheusListener(
            requestToHistogramNameMapper,
            requestToInProgressGaugeNameMapper,
            (rr: (Request[_, _], Response[_])) => responseToSuccessCounterMapper(rr._1, rr._2),
            (rr: (Request[_, _], Response[_])) => responseToErrorCounterMapper(rr._1, rr._2),
            (r: (Request[_, _], Throwable)) => requestToFailureCounterMapper(r._1, r._2),
            requestToSizeSummaryMapper,
            (rr: (Request[_, _], Response[_])) => responseToSizeSummaryMapper(rr._1, rr._2),
            collectorRegistry,
            cacheFor(histograms, collectorRegistry),
            cacheFor(gauges, collectorRegistry),
            cacheFor(counters, collectorRegistry),
            cacheFor(summaries, collectorRegistry)
          ),
          delegate.responseMonad
        )
      )
    )
  }

  /** Clear cached collectors (gauges and histograms) both from the given collector registry, and from the backend.
    */
  def clear(collectorRegistry: CollectorRegistry): Unit = {
    collectorRegistry.clear()
    histograms.remove(collectorRegistry)
    gauges.remove(collectorRegistry)
    counters.remove(collectorRegistry)
    summaries.remove(collectorRegistry)
    ()
  }

  /*
  Each collector can be registered in a collector registry only once - however there might be multiple backends registered
  with the same collector (trying to register a collector under the same name twice results in an exception).
  Hence, we need to store a global cache o created histograms/gauges, so that we can properly re-use them.
   */

  private val histograms = new mutable.WeakHashMap[CollectorRegistry, ConcurrentHashMap[String, Histogram]]
  private val gauges = new mutable.WeakHashMap[CollectorRegistry, ConcurrentHashMap[String, Gauge]]
  private val counters = new mutable.WeakHashMap[CollectorRegistry, ConcurrentHashMap[String, Counter]]
  private val summaries = new mutable.WeakHashMap[CollectorRegistry, ConcurrentHashMap[String, Summary]]

  private def cacheFor[T](
      cache: mutable.WeakHashMap[CollectorRegistry, ConcurrentHashMap[String, T]],
      collectorRegistry: CollectorRegistry
  ): ConcurrentHashMap[String, T] =
    cache.synchronized {
      cache.getOrElseUpdate(collectorRegistry, new ConcurrentHashMap[String, T]())
    }

  type RequestCollectors = (Option[Histogram.Timer], Option[Gauge.Child])
}

class PrometheusListener(
    requestToHistogramNameMapper: Request[_, _] => Option[HistogramCollectorConfig],
    requestToInProgressGaugeNameMapper: Request[_, _] => Option[CollectorConfig],
    requestToSuccessCounterMapper: ((Request[_, _], Response[_])) => Option[CollectorConfig],
    requestToErrorCounterMapper: ((Request[_, _], Response[_])) => Option[CollectorConfig],
    requestToFailureCounterMapper: ((Request[_, _], Exception)) => Option[CollectorConfig],
    requestToSizeSummaryMapper: Request[_, _] => Option[CollectorConfig],
    responseToSizeSummaryMapper: ((Request[_, _], Response[_])) => Option[CollectorConfig],
    collectorRegistry: CollectorRegistry,
    histogramsCache: ConcurrentHashMap[String, Histogram],
    gaugesCache: ConcurrentHashMap[String, Gauge],
    countersCache: ConcurrentHashMap[String, Counter],
    summariesCache: ConcurrentHashMap[String, Summary]
) extends RequestListener[Identity, RequestCollectors] {

  override def beforeRequest(request: Request[_, _]): RequestCollectors = {
    val requestTimer: Option[Histogram.Timer] = for {
      histogramData <- requestToHistogramNameMapper(request)
      histogram: Histogram = getOrCreateMetric(histogramsCache, histogramData, createNewHistogram)
    } yield histogram.labels(histogramData.labelValues: _*).startTimer()

    val gauge: Option[Gauge.Child] = for {
      gaugeData <- requestToInProgressGaugeNameMapper(request)
    } yield getOrCreateMetric(gaugesCache, gaugeData, createNewGauge).labels(gaugeData.labelValues: _*)

    observeRequestContentLengthSummaryIfMapped(request, requestToSizeSummaryMapper)

    gauge.foreach(_.inc())

    (requestTimer, gauge)
  }

  override def requestException(
      request: Request[_, _],
      requestCollectors: RequestCollectors,
      e: Exception
  ): Unit = {
    HttpError.find(e) match {
      case Some(HttpError(body, statusCode)) =>
        requestSuccessful(request, Response(body, statusCode), requestCollectors)
      case _ =>
        requestCollectors._1.foreach(_.observeDuration())
        requestCollectors._2.foreach(_.dec())
        incCounterIfMapped((request, e), requestToFailureCounterMapper)
    }
  }

  override def requestSuccessful(
      request: Request[_, _],
      response: Response[_],
      requestCollectors: RequestCollectors
  ): Unit = {
    requestCollectors._1.foreach(_.observeDuration())
    requestCollectors._2.foreach(_.dec())
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
      getOrCreateMetric(countersCache, data, createNewCounter).labels(data.labelValues: _*).inc()
    }

  private def observeResponseContentLengthSummaryIfMapped(
      request: Request[_, _],
      response: Response[_],
      mapper: ((Request[_, _], Response[_])) => Option[BaseCollectorConfig]
  ): Unit =
    mapper((request, response)).foreach { data =>
      response.contentLength.map(_.toDouble).foreach { size =>
        getOrCreateMetric(summariesCache, data, createNewSummary).labels(data.labelValues: _*).observe(size)
      }
    }

  private def observeRequestContentLengthSummaryIfMapped(
      request: Request[_, _],
      mapper: Request[_, _] => Option[BaseCollectorConfig]
  ): Unit =
    mapper(request).foreach { data =>
      (request.contentLength: Option[Long]).map(_.toDouble).foreach { size =>
        getOrCreateMetric(summariesCache, data, createNewSummary).labels(data.labelValues: _*).observe(size)
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
      .build()
      .buckets(data.buckets: _*)
      .name(data.collectorName)
      .labelNames(data.labelNames: _*)
      .help(data.collectorName)
      .register(collectorRegistry)

  private def createNewGauge(data: BaseCollectorConfig): Gauge =
    Gauge
      .build()
      .name(data.collectorName)
      .labelNames(data.labelNames: _*)
      .help(data.collectorName)
      .register(collectorRegistry)

  private def createNewCounter(data: BaseCollectorConfig): Counter =
    Counter
      .build()
      .name(data.collectorName)
      .labelNames(data.labelNames: _*)
      .help(data.collectorName)
      .register(collectorRegistry)

  private def createNewSummary(data: BaseCollectorConfig): Summary =
    Summary
      .build()
      .name(data.collectorName)
      .labelNames(data.labelNames: _*)
      .help(data.collectorName)
      .register(collectorRegistry)
}

trait BaseCollectorConfig {
  def collectorName: String
  def labels: List[(String, String)]

  def labelNames: Seq[String] = labels.map(_._1)
  def labelValues: Seq[String] = labels.map(_._2)
}

/** Represents the name of a collector, together with label names and values. The same labels must be always returned,
  * and in the same order.
  */
case class CollectorConfig(collectorName: String, labels: List[(String, String)] = Nil) extends BaseCollectorConfig

/** Represents the name of a collector with configurable histogram buckets.
  */
case class HistogramCollectorConfig(
    collectorName: String,
    labels: List[(String, String)] = Nil,
    buckets: List[Double] = HistogramCollectorConfig.DefaultBuckets
) extends BaseCollectorConfig

object HistogramCollectorConfig {
  val DefaultBuckets = List(.005, .01, .025, .05, .075, .1, .25, .5, .75, 1, 2.5, 5, 7.5, 10)
}
