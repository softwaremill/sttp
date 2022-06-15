package sttp.client3.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.{DoubleHistogram, LongCounter, LongUpDownCounter, Meter}
import sttp.client3._
import sttp.client3.listener.{ListenerBackend, RequestListener}

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

  def apply[F[_], P](
      delegate: SttpBackend[F, P],
      openTelemetry: OpenTelemetry,
      meterConfig: MeterConfig = MeterConfig.Default,
      clock: Clock = Clock.systemUTC(),
      requestToLatencyHistogramMapper: Request[_, _] => Option[CollectorConfig] = (_: Request[_, _]) =>
        Some(CollectorConfig(DefaultLatencyHistogramName, unit = Some(CollectorConfig.Milliseconds))),
      requestToInProgressCounterMapper: Request[_, _] => Option[CollectorConfig] = (_: Request[_, _]) =>
        Some(CollectorConfig(DefaultRequestsInProgressCounterName)),
      responseToSuccessCounterMapper: Response[_] => Option[CollectorConfig] = (_: Response[_]) =>
        Some(CollectorConfig(DefaultSuccessCounterName)),
      responseToErrorCounterMapper: Response[_] => Option[CollectorConfig] = (_: Response[_]) =>
        Some(CollectorConfig(DefaultErrorCounterName)),
      requestToFailureCounterMapper: (Request[_, _], Throwable) => Option[CollectorConfig] =
        (_: Request[_, _], _: Throwable) => Some(CollectorConfig(DefaultFailureCounterName)),
      requestToSizeHistogramMapper: Request[_, _] => Option[CollectorConfig] = (_: Request[_, _]) =>
        Some(CollectorConfig(DefaultRequestSizeHistogramName, unit = Some(CollectorConfig.Bytes))),
      responseToSizeHistogramMapper: Response[_] => Option[CollectorConfig] = (_: Response[_]) =>
        Some(CollectorConfig(DefaultResponseSizeHistogramName, unit = Some(CollectorConfig.Bytes)))
  ): SttpBackend[F, P] = usingMeter(
    delegate,
    openTelemetry.meterBuilder(meterConfig.name).setInstrumentationVersion(meterConfig.version).build(),
    clock,
    requestToLatencyHistogramMapper = requestToLatencyHistogramMapper,
    requestToInProgressCounterMapper = requestToInProgressCounterMapper,
    responseToSuccessCounterMapper = responseToSuccessCounterMapper,
    responseToErrorCounterMapper = responseToErrorCounterMapper,
    requestToFailureCounterMapper = requestToFailureCounterMapper,
    requestToSizeHistogramMapper = requestToSizeHistogramMapper,
    responseToSizeHistogramMapper = responseToSizeHistogramMapper
  )

  def usingMeter[F[_], P](
      delegate: SttpBackend[F, P],
      meter: Meter,
      clock: Clock = Clock.systemUTC(),
      requestToLatencyHistogramMapper: Request[_, _] => Option[CollectorConfig] = (_: Request[_, _]) =>
        Some(CollectorConfig(DefaultLatencyHistogramName, unit = Some(CollectorConfig.Milliseconds))),
      requestToInProgressCounterMapper: Request[_, _] => Option[CollectorConfig] = (_: Request[_, _]) =>
        Some(CollectorConfig(DefaultRequestsInProgressCounterName)),
      responseToSuccessCounterMapper: Response[_] => Option[CollectorConfig] = (_: Response[_]) =>
        Some(CollectorConfig(DefaultSuccessCounterName)),
      responseToErrorCounterMapper: Response[_] => Option[CollectorConfig] = (_: Response[_]) =>
        Some(CollectorConfig(DefaultErrorCounterName)),
      requestToFailureCounterMapper: (Request[_, _], Throwable) => Option[CollectorConfig] =
        (_: Request[_, _], _: Throwable) => Some(CollectorConfig(DefaultFailureCounterName)),
      requestToSizeHistogramMapper: Request[_, _] => Option[CollectorConfig] = (_: Request[_, _]) =>
        Some(CollectorConfig(DefaultRequestSizeHistogramName, unit = Some(CollectorConfig.Bytes))),
      responseToSizeHistogramMapper: Response[_] => Option[CollectorConfig] = (_: Response[_]) =>
        Some(CollectorConfig(DefaultResponseSizeHistogramName, unit = Some(CollectorConfig.Bytes)))
  ): SttpBackend[F, P] = {
    // redirects should be handled before metrics
    new FollowRedirectsBackend[F, P](
      new ListenerBackend[F, P, Option[Long]](
        delegate,
        RequestListener.lift(
          new OpenTelemetryMetricsListener(
            meter,
            clock,
            requestToLatencyHistogramMapper,
            requestToInProgressCounterMapper,
            responseToSuccessCounterMapper,
            responseToErrorCounterMapper,
            requestToFailureCounterMapper,
            requestToSizeHistogramMapper,
            responseToSizeHistogramMapper
          ),
          delegate.responseMonad
        )
      )
    )
  }
}

private class OpenTelemetryMetricsListener(
    meter: Meter,
    clock: Clock,
    requestToLatencyHistogramMapper: Request[_, _] => Option[CollectorConfig],
    requestToInProgressCounterMapper: Request[_, _] => Option[CollectorConfig],
    responseToSuccessCounterMapper: Response[_] => Option[CollectorConfig],
    requestToErrorCounterMapper: Response[_] => Option[CollectorConfig],
    requestToFailureCounterMapper: (Request[_, _], Throwable) => Option[CollectorConfig],
    requestToSizeHistogramMapper: Request[_, _] => Option[CollectorConfig],
    responseToSizeHistogramMapper: Response[_] => Option[CollectorConfig]
) extends RequestListener[Identity, Option[Long]] {

  private val counters = new ConcurrentHashMap[String, LongCounter]
  private val histograms = new ConcurrentHashMap[String, DoubleHistogram]()
  private val upAndDownCounter = new ConcurrentHashMap[String, LongUpDownCounter]()

  override def beforeRequest(request: Request[_, _]): Option[Long] = {
    updateInProgressCounter(request, 1)
    recordHistogram(requestToSizeHistogramMapper(request), request.contentLength)
    requestToLatencyHistogramMapper(request).map(_ => clock.millis())
  }

  override def requestSuccessful(request: Request[_, _], response: Response[_], tag: Option[Long]): Unit = {
    if (response.isSuccess) {
      incrementCounter(responseToSuccessCounterMapper(response))
    } else {
      incrementCounter(requestToErrorCounterMapper(response))
    }
    recordHistogram(responseToSizeHistogramMapper(response), response.contentLength)
    recordHistogram(requestToLatencyHistogramMapper(request), tag.map(clock.millis() - _))
    updateInProgressCounter(request, -1)
  }

  override def requestException(request: Request[_, _], tag: Option[Long], e: Exception): Unit = {
    HttpError.find(e) match {
      case Some(HttpError(body, statusCode)) =>
        requestSuccessful(request, Response(body, statusCode), tag)
      case _ =>
        incrementCounter(requestToFailureCounterMapper(request, e))
        recordHistogram(requestToLatencyHistogramMapper(request), tag.map(clock.millis() - _))
        updateInProgressCounter(request, -1)
    }
  }

  private def updateInProgressCounter[R, T](request: Request[T, R], delta: Long): Unit = {
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
  val Default: MeterConfig = MeterConfig("sttp-client3", "1.0.0")
}
