package sttp.client3.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.{DoubleHistogram, LongCounter, LongUpDownCounter, Meter}
import sttp.capabilities.Effect
import sttp.client3._
import sttp.monad.MonadError
import sttp.monad.syntax.MonadErrorOps

import java.util.concurrent.ConcurrentHashMap

private class OpenTelemetryMetricsBackend[F[_], P](
    delegate: SttpBackend[F, P],
    openTelemetry: OpenTelemetry,
    meterConfig: Option[MeterConfig],
    requestToInProgressCounterNameMapper: Request[_, _] => Option[CollectorConfig],
    responseToSuccessCounterMapper: Response[_] => Option[CollectorConfig],
    requestToErrorCounterMapper: Response[_] => Option[CollectorConfig],
    requestToFailureCounterMapper: (Request[_, _], Throwable) => Option[CollectorConfig],
    requestToSizeHistogramMapper: Request[_, _] => Option[CollectorConfig],
    responseToSizeHistogramMapper: Response[_] => Option[CollectorConfig]
) extends SttpBackend[F, P] {

  private val meter: Meter = meterConfig
    .map(config =>
      openTelemetry
        .meterBuilder(config.name)
        .setInstrumentationVersion(config.version)
    )
    .getOrElse(openTelemetry.meterBuilder("sttp3-client"))
    .build()

  private val counters: ConcurrentHashMap[String, LongCounter] = new ConcurrentHashMap[String, LongCounter]
  private val histograms = new ConcurrentHashMap[String, DoubleHistogram]()
  private val upAndDownCounter = new ConcurrentHashMap[String, LongUpDownCounter]()

  private implicit val _monad: MonadError[F] = responseMonad
  type PE = P with Effect[F]

  def send[T, R >: PE](request: Request[T, R]): F[Response[T]] = {
    responseMonad
      .eval(before(request))
      .flatMap(_ =>
        responseMonad.handleError(
          delegate.send(request).map { response =>
            if (response.isSuccess) {
              incrementCounter(responseToSuccessCounterMapper(response))
            } else {
              incrementCounter(requestToErrorCounterMapper(response))
            }
            decrementUpDownCounter(request)
            responseToSizeHistogramMapper(response)
              .foreach(config =>
                getOrCreateMetric(histograms, config, createNewHistogram)
                  .record((response.contentLength: Option[Long]).map(_.toDouble).getOrElse(0), config.attributes)
              )
            response
          }
        ) { case e =>
          after(request, e)
          responseMonad.error(e)
        }
      )
  }

  private def before[R >: PE, T](request: Request[T, R]): Unit = {
    requestToInProgressCounterNameMapper(request)
      .foreach(config => getOrCreateMetric(upAndDownCounter, config, createNewUpDownCounter).add(1, config.attributes))
    requestToSizeHistogramMapper(request)
      .foreach(config =>
        getOrCreateMetric(histograms, config, createNewHistogram)
          .record((request.contentLength: Option[Long]).map(_.toDouble).getOrElse(0), config.attributes)
      )
  }

  private def after[R >: PE, T](request: Request[T, R], e: Throwable): Unit = {
    incrementCounter(requestToFailureCounterMapper(request, e))
    decrementUpDownCounter(request)
  }

  private def decrementUpDownCounter[R >: PE, T](request: Request[T, R]): Unit = {
    requestToInProgressCounterNameMapper(request)
      .foreach(config => getOrCreateMetric(upAndDownCounter, config, createNewUpDownCounter).add(-1, config.attributes))
  }

  private def incrementCounter(collectorConfig: Option[CollectorConfig]): Unit = {
    collectorConfig
      .foreach(config => getOrCreateMetric(counters, config, createNewCounter).add(1, config.attributes))
  }

  override def close(): F[Unit] = delegate.close()

  override def responseMonad: MonadError[F] = delegate.responseMonad

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

  private def createNewUpDownCounter(collectorConfig: CollectorConfig): LongUpDownCounter =
    meter
      .upDownCounterBuilder(collectorConfig.name)
      .setUnit(collectorConfig.unit)
      .setDescription(collectorConfig.description)
      .build()

  private def createNewCounter(collectorConfig: CollectorConfig): LongCounter =
    meter
      .counterBuilder(collectorConfig.name)
      .setUnit(collectorConfig.unit)
      .setDescription(collectorConfig.description)
      .build()

  private def createNewHistogram(collectorConfig: CollectorConfig): DoubleHistogram =
    meter
      .histogramBuilder(collectorConfig.name)
      .setUnit(collectorConfig.unit)
      .setDescription(collectorConfig.description)
      .build()
}

case class CollectorConfig(
    name: String,
    description: String = "",
    unit: String = "",
    attributes: Attributes = Attributes.empty()
)
case class MeterConfig(name: String, version: String)

object OpenTelemetryMetricsBackend {

  val DefaultRequestsInProgressCounterName = "sttp3_requests_in_progress"
  val DefaultSuccessCounterName = "sttp3_requests_success_count"
  val DefaultErrorCounterName = "sttp3_requests_error_count"
  val DefaultFailureCounterName = "sttp3_requests_failure_count"
  val DefaultRequestHistogramName = "sttp3_request_size_bytes"
  val DefaultResponseHistogramName = "sttp3_response_size_bytes"

  def apply[F[_], P](
      delegate: SttpBackend[F, P],
      openTelemetry: OpenTelemetry,
      meterConfig: Option[MeterConfig] = None,
      requestToInProgressCounterNameMapper: Request[_, _] => Option[CollectorConfig] = (_: Request[_, _]) =>
        Some(CollectorConfig(DefaultRequestsInProgressCounterName)),
      responseToSuccessCounterMapper: Response[_] => Option[CollectorConfig] = (_: Response[_]) =>
        Some(CollectorConfig(DefaultSuccessCounterName)),
      responseToErrorCounterMapper: Response[_] => Option[CollectorConfig] = (_: Response[_]) =>
        Some(CollectorConfig(DefaultErrorCounterName)),
      requestToFailureCounterMapper: (Request[_, _], Throwable) => Option[CollectorConfig] =
        (_: Request[_, _], _: Throwable) => Some(CollectorConfig(DefaultFailureCounterName)),
      requestToSizeSummaryMapper: Request[_, _] => Option[CollectorConfig] = (_: Request[_, _]) =>
        Some(CollectorConfig(DefaultRequestHistogramName)),
      responseToSizeSummaryMapper: Response[_] => Option[CollectorConfig] = (_: Response[_]) =>
        Some(CollectorConfig(DefaultResponseHistogramName))
  ): SttpBackend[F, P] =
    new OpenTelemetryMetricsBackend[F, P](
      delegate,
      openTelemetry,
      meterConfig,
      requestToInProgressCounterNameMapper = requestToInProgressCounterNameMapper,
      responseToSuccessCounterMapper = responseToSuccessCounterMapper,
      requestToErrorCounterMapper = responseToErrorCounterMapper,
      requestToFailureCounterMapper = requestToFailureCounterMapper,
      requestToSizeHistogramMapper = requestToSizeSummaryMapper,
      responseToSizeHistogramMapper = responseToSizeSummaryMapper
    )
}
