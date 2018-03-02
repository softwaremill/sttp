package com.softwaremill.sttp.prometheus

import java.util.concurrent.ConcurrentHashMap

import com.softwaremill.sttp.{FollowRedirectsBackend, MonadError, Request, Response, SttpBackend}
import io.prometheus.client.{Gauge, Histogram}

import scala.collection.mutable
import scala.language.higherKinds
import scala.collection.JavaConverters._

class PrometheusBackend[R[_], S] private (delegate: SttpBackend[R, S],
                                          requestToHistogramNameMapper: Request[_, S] => Option[String],
                                          requestToInProgressGaugeNameMapper: Request[_, S] => Option[String])
    extends SttpBackend[R, S] {

  private[this] val histograms: mutable.Map[String, Histogram] = new ConcurrentHashMap[String, Histogram]().asScala
  private[this] val gauges: mutable.Map[String, Gauge] = new ConcurrentHashMap[String, Gauge]().asScala

  override def send[T](request: Request[T, S]): R[Response[T]] = {
    val requestTimer: Option[Histogram.Timer] = for {
      histogramName: String <- requestToHistogramNameMapper(request)
      histogram: Histogram = histograms.getOrElseUpdate(histogramName, createNewHistogram(histogramName))
    } yield histogram.startTimer()

    val gauge: Option[Gauge] = for {
      gaugeName: String <- requestToInProgressGaugeNameMapper(request)
    } yield gauges.getOrElseUpdate(gaugeName, createNewGauge(gaugeName))

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

  private[this] def createNewHistogram(name: String): Histogram = Histogram.build().name(name).help(name).register()

  private[this] def createNewGauge(name: String): Gauge = Gauge.build().name(name).help(name).register()
}

object PrometheusBackend {

  val DefaultHistogramName = "sttp_request_latency"
  val DefaultRequestsInProgressGaugeName = "sttp_requests_in_progress"

  def apply[R[_], S](delegate: SttpBackend[R, S],
                     requestToHistogramNameMapper: Request[_, S] => Option[String] = (_: Request[_, S]) =>
                       Some(DefaultHistogramName),
                     requestToInProgressGaugeNameMapper: Request[_, S] => Option[String] = (_: Request[_, S]) =>
                       Some(DefaultRequestsInProgressGaugeName)): SttpBackend[R, S] = {
    // redirects should be handled before prometheus
    new FollowRedirectsBackend(
      new PrometheusBackend(delegate, requestToHistogramNameMapper, requestToInProgressGaugeNameMapper))
  }
}
