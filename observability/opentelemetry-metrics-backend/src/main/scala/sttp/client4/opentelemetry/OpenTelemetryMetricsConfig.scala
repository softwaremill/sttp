package sttp.client4.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.metrics.Meter
import sttp.client4._
import sttp.client4.opentelemetry.OpenTelemetryMetricsBackend._

import java.time.Clock

final case class OpenTelemetryMetricsConfig(
    meter: Meter,
    clock: Clock,
    requestToLatencyHistogramMapper: GenericRequest[_, _] => Option[HistogramCollectorConfig],
    requestToInProgressCounterMapper: GenericRequest[_, _] => Option[CollectorConfig],
    responseToSuccessCounterMapper: Response[_] => Option[CollectorConfig],
    requestToErrorCounterMapper: Response[_] => Option[CollectorConfig],
    requestToFailureCounterMapper: (GenericRequest[_, _], Throwable) => Option[CollectorConfig],
    requestToSizeHistogramMapper: GenericRequest[_, _] => Option[HistogramCollectorConfig],
    responseToSizeHistogramMapper: Response[_] => Option[HistogramCollectorConfig]
)

object OpenTelemetryMetricsConfig {
  def apply(
      openTelemetry: OpenTelemetry,
      meterConfig: MeterConfig = MeterConfig.Default,
      clock: Clock = Clock.systemUTC(),
      requestToLatencyHistogramMapper: GenericRequest[_, _] => Option[HistogramCollectorConfig] =
        (_: GenericRequest[_, _]) =>
          Some(
            HistogramCollectorConfig(
              DefaultLatencyHistogramName,
              buckets = HistogramCollectorConfig.DefaultLatencyBuckets,
              unit = HistogramCollectorConfig.Milliseconds
            )
          ),
      requestToInProgressCounterMapper: GenericRequest[_, _] => Option[CollectorConfig] = (_: GenericRequest[_, _]) =>
        Some(CollectorConfig(DefaultRequestsActiveCounterName)),
      responseToSuccessCounterMapper: Response[_] => Option[CollectorConfig] = (_: Response[_]) =>
        Some(CollectorConfig(DefaultSuccessCounterName)),
      responseToErrorCounterMapper: Response[_] => Option[CollectorConfig] = (_: Response[_]) =>
        Some(CollectorConfig(DefaultErrorCounterName)),
      requestToFailureCounterMapper: (GenericRequest[_, _], Throwable) => Option[CollectorConfig] =
        (_: GenericRequest[_, _], _: Throwable) => Some(CollectorConfig(DefaultFailureCounterName)),
      requestToSizeHistogramMapper: GenericRequest[_, _] => Option[HistogramCollectorConfig] =
        (_: GenericRequest[_, _]) =>
          Some(
            HistogramCollectorConfig(
              DefaultRequestSizeHistogramName,
              buckets = HistogramCollectorConfig.DefaultSizeBuckets,
              unit = HistogramCollectorConfig.Bytes
            )
          ),
      responseToSizeHistogramMapper: Response[_] => Option[HistogramCollectorConfig] = (_: Response[_]) =>
        Some(
          HistogramCollectorConfig(
            DefaultResponseSizeHistogramName,
            buckets = HistogramCollectorConfig.DefaultSizeBuckets,
            unit = HistogramCollectorConfig.Bytes
          )
        ),
  ): OpenTelemetryMetricsConfig = usingMeter(
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

  def usingMeter(
      meter: Meter,
      clock: Clock = Clock.systemUTC(),
      requestToLatencyHistogramMapper: GenericRequest[_, _] => Option[HistogramCollectorConfig] =
        (_: GenericRequest[_, _]) =>
          Some(
            HistogramCollectorConfig(
              DefaultLatencyHistogramName,
              buckets = HistogramCollectorConfig.DefaultLatencyBuckets,
              unit = HistogramCollectorConfig.Milliseconds
            )
          ),
      requestToInProgressCounterMapper: GenericRequest[_, _] => Option[CollectorConfig] = (_: GenericRequest[_, _]) =>
        Some(CollectorConfig(DefaultRequestsActiveCounterName)),
      responseToSuccessCounterMapper: Response[_] => Option[CollectorConfig] = (_: Response[_]) =>
        Some(CollectorConfig(DefaultSuccessCounterName)),
      responseToErrorCounterMapper: Response[_] => Option[CollectorConfig] = (_: Response[_]) =>
        Some(CollectorConfig(DefaultErrorCounterName)),
      requestToFailureCounterMapper: (GenericRequest[_, _], Throwable) => Option[CollectorConfig] =
        (_: GenericRequest[_, _], _: Throwable) => Some(CollectorConfig(DefaultFailureCounterName)),
      requestToSizeHistogramMapper: GenericRequest[_, _] => Option[HistogramCollectorConfig] =
        (_: GenericRequest[_, _]) =>
          Some(
            HistogramCollectorConfig(
              DefaultRequestSizeHistogramName,
              buckets = HistogramCollectorConfig.DefaultSizeBuckets,
              unit = HistogramCollectorConfig.Bytes
            )
          ),
      responseToSizeHistogramMapper: Response[_] => Option[HistogramCollectorConfig] = (_: Response[_]) =>
        Some(
          HistogramCollectorConfig(
            DefaultResponseSizeHistogramName,
            buckets = HistogramCollectorConfig.DefaultSizeBuckets,
            unit = HistogramCollectorConfig.Bytes
          )
        )
  ): OpenTelemetryMetricsConfig =
    OpenTelemetryMetricsConfig(
      meter,
      clock,
      requestToLatencyHistogramMapper,
      requestToInProgressCounterMapper,
      responseToSuccessCounterMapper,
      responseToErrorCounterMapper,
      requestToFailureCounterMapper,
      requestToSizeHistogramMapper,
      responseToSizeHistogramMapper
    )
}
