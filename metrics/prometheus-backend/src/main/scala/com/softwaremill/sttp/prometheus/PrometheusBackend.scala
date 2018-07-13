package com.softwaremill.sttp.prometheus

import java.util.concurrent.ConcurrentHashMap

import com.softwaremill.sttp.{FollowRedirectsBackend, MonadError, Request, Response, SttpBackend}
import io.prometheus.client.{CollectorRegistry, Gauge, Histogram}

import scala.collection.mutable
import scala.language.higherKinds
import scala.collection.JavaConverters._

class PrometheusBackend[R[_], S] private (delegate: SttpBackend[R, S],
                                          requestToHistogramNameMapper: Request[_, S] => Option[String],
                                          requestToInProgressGaugeNameMapper: Request[_, S] => Option[String],
                                          collectorRegistry: CollectorRegistry,
                                          histogramsCache: mutable.Map[String, Histogram],
                                          gaugesCache: mutable.Map[String, Gauge])
    extends SttpBackend[R, S] {

  override def send[T](request: Request[T, S]): R[Response[T]] = {
    val requestTimer: Option[Histogram.Timer] = for {
      histogramName: String <- requestToHistogramNameMapper(request)
      histogram: Histogram = histogramsCache.getOrElseUpdate(histogramName, createNewHistogram(histogramName))
    } yield histogram.startTimer()

    val gauge: Option[Gauge] = for {
      gaugeName: String <- requestToInProgressGaugeNameMapper(request)
    } yield gaugesCache.getOrElseUpdate(gaugeName, createNewGauge(gaugeName))

    gauge.foreach(_.inc())

    responseMonad.handleError(
      responseMonad.map(delegate.send(request)) { response =>
        requestTimer.foreach(_.observeDuration())
        gauge.foreach(_.dec())
        response
      }
    ) {
      case e: Exception =>
        requestTimer.foreach(_.observeDuration())
        gauge.foreach(_.dec())
        responseMonad.error(e)
    }
  }

  override def close(): Unit = delegate.close()

  override def responseMonad: MonadError[R] = delegate.responseMonad

  private[this] def createNewHistogram(name: String): Histogram =
    Histogram.build().name(name).help(name).register(collectorRegistry)

  private[this] def createNewGauge(name: String): Gauge =
    Gauge.build().name(name).help(name).register(collectorRegistry)
}

object PrometheusBackend {

  val DefaultHistogramName = "sttp_request_latency"
  val DefaultRequestsInProgressGaugeName = "sttp_requests_in_progress"

  def apply[R[_], S](delegate: SttpBackend[R, S],
                     requestToHistogramNameMapper: Request[_, S] => Option[String] = (_: Request[_, S]) =>
                       Some(DefaultHistogramName),
                     requestToInProgressGaugeNameMapper: Request[_, S] => Option[String] = (_: Request[_, S]) =>
                       Some(DefaultRequestsInProgressGaugeName),
                     collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry): SttpBackend[R, S] = {
    // redirects should be handled before prometheus
    new FollowRedirectsBackend(
      new PrometheusBackend(
        delegate,
        requestToHistogramNameMapper,
        requestToInProgressGaugeNameMapper,
        collectorRegistry,
        histogramsCacheFor(collectorRegistry),
        gaugesCacheFor(collectorRegistry)
      ))
  }

  /**
    * Clear cached collectors (gauges and histograms) both from the given collector registry, and from the backend.
    */
  def clear(collectorRegistry: CollectorRegistry): Unit = {
    collectorRegistry.clear()
    histograms.remove(collectorRegistry)
    gauges.remove(collectorRegistry)
  }

  /*
  Each collector can be registered in a collector registry only once - however there might be multiple backends registered
  with the same collector (trying to register a collector under the same name twice results in an exception).
  Hence, we need to store a global cache o created histograms/gauges, so that we can properly re-use them.
   */

  private val histograms = new mutable.WeakHashMap[CollectorRegistry, mutable.Map[String, Histogram]]
  private val gauges = new mutable.WeakHashMap[CollectorRegistry, mutable.Map[String, Gauge]]

  private def histogramsCacheFor(collectorRegistry: CollectorRegistry): mutable.Map[String, Histogram] =
    histograms.synchronized {
      histograms.getOrElseUpdate(collectorRegistry, new ConcurrentHashMap[String, Histogram]().asScala)
    }

  private def gaugesCacheFor(collectorRegistry: CollectorRegistry): mutable.Map[String, Gauge] = gauges.synchronized {
    gauges.getOrElseUpdate(collectorRegistry, new ConcurrentHashMap[String, Gauge]().asScala)
  }
}
