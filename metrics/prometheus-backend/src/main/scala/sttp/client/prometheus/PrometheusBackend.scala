package sttp.client.prometheus

import java.util.concurrent.ConcurrentHashMap

import com.github.ghik.silencer.silent
import sttp.client.{FollowRedirectsBackend, Identity, NothingT, Request, Response, SttpBackend}
import io.prometheus.client.{CollectorRegistry, Counter, Gauge, Histogram}
import sttp.client.listener.{ListenerBackend, RequestListener}
import sttp.client.prometheus.PrometheusBackend.RequestCollectors
import sttp.client.ws.WebSocketResponse

import scala.collection.mutable
import scala.language.higherKinds

object PrometheusBackend {
  val DefaultHistogramName = "sttp_request_latency"
  val DefaultRequestsInProgressGaugeName = "sttp_requests_in_progress"
  val DefaultSuccessCounterName = "sttp_requests_success_count"
  val DefaultErrorCounterName = "sttp_requests_error_count"
  val DefaultFailureCounterName = "sttp_requests_failure_count"

  def apply[F[_], S, WS_HANDLER[_]](
      delegate: SttpBackend[F, S, WS_HANDLER],
      requestToHistogramNameMapper: Request[_, _] => Option[CollectorNameWithLabels] = (_: Request[_, _]) =>
        Some(CollectorNameWithLabels(DefaultHistogramName)),
      requestToInProgressGaugeNameMapper: Request[_, _] => Option[CollectorNameWithLabels] = (_: Request[_, _]) =>
        Some(CollectorNameWithLabels(DefaultRequestsInProgressGaugeName)),
      requestToSuccessCounterMapper: Request[_, _] => Option[CollectorNameWithLabels] = (_: Request[_, _]) =>
        Some(CollectorNameWithLabels(DefaultSuccessCounterName)),
      requestToErrorCounterMapper: Request[_, _] => Option[CollectorNameWithLabels] = (_: Request[_, _]) =>
        Some(CollectorNameWithLabels(DefaultErrorCounterName)),
      requestToFailureCounterMapper: Request[_, _] => Option[CollectorNameWithLabels] = (_: Request[_, _]) =>
        Some(CollectorNameWithLabels(DefaultFailureCounterName)),
      collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
  ): SttpBackend[F, S, WS_HANDLER] = {
    // redirects should be handled before prometheus
    new FollowRedirectsBackend[F, S, WS_HANDLER](
      new ListenerBackend[F, S, WS_HANDLER, RequestCollectors](
        delegate,
        RequestListener.lift(
          new PrometheusListener(
            requestToHistogramNameMapper,
            requestToInProgressGaugeNameMapper,
            requestToSuccessCounterMapper,
            requestToErrorCounterMapper,
            requestToFailureCounterMapper,
            collectorRegistry,
            cacheFor(histograms, collectorRegistry),
            cacheFor(gauges, collectorRegistry),
            cacheFor(counters, collectorRegistry)
          ),
          delegate.responseMonad
        )
      )
    )
  }

  /**
    * Clear cached collectors (gauges and histograms) both from the given collector registry, and from the backend.
    */
  @silent("discarded")
  def clear(collectorRegistry: CollectorRegistry): Unit = {
    collectorRegistry.clear()
    histograms.remove(collectorRegistry)
    gauges.remove(collectorRegistry)
    counters.remove(collectorRegistry)
  }

  /*
  Each collector can be registered in a collector registry only once - however there might be multiple backends registered
  with the same collector (trying to register a collector under the same name twice results in an exception).
  Hence, we need to store a global cache o created histograms/gauges, so that we can properly re-use them.
   */

  private val histograms = new mutable.WeakHashMap[CollectorRegistry, ConcurrentHashMap[String, Histogram]]
  private val gauges = new mutable.WeakHashMap[CollectorRegistry, ConcurrentHashMap[String, Gauge]]
  private val counters = new mutable.WeakHashMap[CollectorRegistry, ConcurrentHashMap[String, Counter]]

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
    requestToHistogramNameMapper: Request[_, _] => Option[CollectorNameWithLabels],
    requestToInProgressGaugeNameMapper: Request[_, _] => Option[CollectorNameWithLabels],
    requestToSuccessCounterMapper: Request[_, _] => Option[CollectorNameWithLabels],
    requestToErrorCounterMapper: Request[_, _] => Option[CollectorNameWithLabels],
    requestToFailureCounterMapper: Request[_, _] => Option[CollectorNameWithLabels],
    collectorRegistry: CollectorRegistry,
    histogramsCache: ConcurrentHashMap[String, Histogram],
    gaugesCache: ConcurrentHashMap[String, Gauge],
    countersCache: ConcurrentHashMap[String, Counter]
) extends RequestListener[Identity, RequestCollectors] {

  override def beforeRequest(request: Request[_, _]): RequestCollectors = {
    val requestTimer: Option[Histogram.Timer] = for {
      histogramData <- requestToHistogramNameMapper(request)
      histogram: Histogram = getOrCreateMetric(histogramsCache, histogramData, createNewHistogram)
    } yield histogram.labels(histogramData.labelValues: _*).startTimer()

    val gauge: Option[Gauge.Child] = for {
      gaugeData <- requestToInProgressGaugeNameMapper(request)
    } yield getOrCreateMetric(gaugesCache, gaugeData, createNewGauge).labels(gaugeData.labelValues: _*)

    gauge.foreach(_.inc())

    (requestTimer, gauge)
  }

  override def requestException(
      request: Request[_, _],
      requestCollectors: RequestCollectors,
      e: Exception
  ): Unit = {
    requestCollectors._1.foreach(_.observeDuration())
    requestCollectors._2.foreach(_.dec())
    incCounterIfMapped(request, requestToFailureCounterMapper)
  }

  override def requestSuccessful(
      request: Request[_, _],
      response: Response[_],
      requestCollectors: RequestCollectors
  ): Unit = {
    requestCollectors._1.foreach(_.observeDuration())
    requestCollectors._2.foreach(_.dec())

    if (response.isSuccess) {
      incCounterIfMapped(request, requestToSuccessCounterMapper)
    } else {
      incCounterIfMapped(request, requestToErrorCounterMapper)
    }
  }

  override def beforeWebsocket(request: Request[_, _]): RequestCollectors = (None, None)

  override def websocketException(
      request: Request[_, _],
      requestCollectors: RequestCollectors,
      e: Exception
  ): Unit = {}

  override def websocketSuccessful(
      request: Request[_, _],
      response: WebSocketResponse[_],
      requestCollectors: RequestCollectors
  ): Unit = {}

  private def incCounterIfMapped[T](
      request: Request[_, _],
      mapper: Request[_, _] => Option[CollectorNameWithLabels]
  ): Unit =
    mapper(request).foreach { data =>
      getOrCreateMetric(countersCache, data, createNewCounter).labels(data.labelValues: _*).inc()
    }

  private def getOrCreateMetric[T](
      cache: ConcurrentHashMap[String, T],
      data: CollectorNameWithLabels,
      create: CollectorNameWithLabels => T
  ): T =
    cache.computeIfAbsent(data.name, new java.util.function.Function[String, T] {
      override def apply(t: String): T = create(data)
    })

  private def createNewHistogram(data: CollectorNameWithLabels): Histogram =
    Histogram.build().name(data.name).labelNames(data.labelNames: _*).help(data.name).register(collectorRegistry)

  private def createNewGauge(data: CollectorNameWithLabels): Gauge =
    Gauge.build().name(data.name).labelNames(data.labelNames: _*).help(data.name).register(collectorRegistry)

  private def createNewCounter(data: CollectorNameWithLabels): Counter =
    Counter.build().name(data.name).labelNames(data.labelNames: _*).help(data.name).register(collectorRegistry)
}

/**
  * Represents the name of a collector, together with label names and values.
  * The same labels must be always returned, and in the same order.
  */
case class CollectorNameWithLabels(name: String, labels: List[(String, String)] = Nil) {
  def labelNames: Seq[String] = labels.map(_._1)
  def labelValues: Seq[String] = labels.map(_._2)
}
