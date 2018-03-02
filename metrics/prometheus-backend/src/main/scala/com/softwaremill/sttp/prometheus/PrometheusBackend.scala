package com.softwaremill.sttp.prometheus

import java.util.concurrent.ConcurrentHashMap

import com.softwaremill.sttp.{FollowRedirectsBackend, MonadError, Request, Response, SttpBackend}
import io.prometheus.client.Histogram

import scala.collection.mutable
import scala.language.higherKinds
import scala.collection.JavaConverters._

class PrometheusBackend[R[_], S] private (delegate: SttpBackend[R, S],
                                          requestToHistogramNameMapper: Option[Request[_, S] => String])
    extends SttpBackend[R, S] {

  import PrometheusBackend._

  private[this] val histograms: mutable.Map[String, Histogram] = new ConcurrentHashMap[String, Histogram]().asScala

  override def send[T](request: Request[T, S]): R[Response[T]] = {
    val histogramName = getHistogramName(request)
    val histogram = histograms.getOrElseUpdate(histogramName, createNewHistogram(histogramName))
    val requestTimer = histogram.startTimer()

    responseMonad.handleError(
      responseMonad.map(delegate.send(request)) { response =>
        requestTimer.observeDuration()
        response
      }
    ) {
      case e: Exception =>
        requestTimer.observeDuration()
        responseMonad.error(e)
    }
  }

  override def close(): Unit = delegate.close()

  override def responseMonad: MonadError[R] = delegate.responseMonad

  private[this] def getHistogramName(request: Request[_, S]): String =
    requestToHistogramNameMapper.map(_.apply(request)).getOrElse(DefaultHistogramName)

  private[this] def createNewHistogram(name: String): Histogram = Histogram.build().name(name).help(name).register()
}

object PrometheusBackend {

  val DefaultHistogramName = "sttp_request_latency"

  def apply[R[_], S](delegate: SttpBackend[R, S],
                     requestToHistogramNameMapper: Option[Request[_, S] => String] = None): SttpBackend[R, S] = {
    // redirects should be handled before brave tracing, hence adding the follow-redirects backend on top
    new FollowRedirectsBackend(new PrometheusBackend(delegate, requestToHistogramNameMapper))
  }
}
