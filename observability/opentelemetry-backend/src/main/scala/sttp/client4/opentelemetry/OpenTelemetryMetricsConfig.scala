package sttp.client4.opentelemetry

import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.metrics.Meter
import sttp.client4._
import sttp.client4.opentelemetry.OpenTelemetryMetricsBackend._

import java.time.Clock
import sttp.model.ResponseMetadata

final case class OpenTelemetryMetricsConfig(
    meter: Meter,
    clock: Clock,
    requestToLatencyHistogramMapper: GenericRequest[?, ?] => Option[HistogramCollectorConfig],
    requestToInProgressCounterMapper: GenericRequest[?, ?] => Option[CollectorConfig],
    responseToSuccessCounterMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[CollectorConfig],
    requestToErrorCounterMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[CollectorConfig],
    requestToFailureCounterMapper: (GenericRequest[?, ?], Throwable) => Option[CollectorConfig],
    requestToSizeHistogramMapper: GenericRequest[?, ?] => Option[HistogramCollectorConfig],
    responseToSizeHistogramMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[HistogramCollectorConfig],
    requestAttributes: GenericRequest[?, ?] => Attributes,
    responseAttributes: (GenericRequest[?, ?], ResponseMetadata) => Attributes,
    errorAttributes: Throwable => Attributes
)

object OpenTelemetryMetricsConfig {
  def apply(
      openTelemetry: OpenTelemetry,
      clock: Clock = Clock.systemUTC(),
      requestToLatencyHistogramMapper: GenericRequest[?, ?] => Option[HistogramCollectorConfig] =
        (_: GenericRequest[?, ?]) =>
          Some(
            HistogramCollectorConfig(
              DefaultLatencyHistogramName,
              buckets = HistogramCollectorConfig.DefaultLatencyBuckets,
              unit = HistogramCollectorConfig.Milliseconds
            )
          ),
      requestToInProgressCounterMapper: GenericRequest[?, ?] => Option[CollectorConfig] = (_: GenericRequest[?, ?]) =>
        Some(CollectorConfig(DefaultRequestsActiveCounterName)),
      responseToSuccessCounterMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[CollectorConfig] =
        (_: GenericRequest[?, ?], _: ResponseMetadata) => Some(CollectorConfig(DefaultSuccessCounterName)),
      responseToErrorCounterMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[CollectorConfig] =
        (_: GenericRequest[?, ?], _: ResponseMetadata) => Some(CollectorConfig(DefaultErrorCounterName)),
      requestToFailureCounterMapper: (GenericRequest[?, ?], Throwable) => Option[CollectorConfig] =
        (_: GenericRequest[?, ?], _: Throwable) => Some(CollectorConfig(DefaultFailureCounterName)),
      requestToSizeHistogramMapper: GenericRequest[?, ?] => Option[HistogramCollectorConfig] =
        (_: GenericRequest[?, ?]) =>
          Some(
            HistogramCollectorConfig(
              DefaultRequestSizeHistogramName,
              buckets = HistogramCollectorConfig.DefaultSizeBuckets,
              unit = HistogramCollectorConfig.Bytes
            )
          ),
      responseToSizeHistogramMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[HistogramCollectorConfig] =
        (_: GenericRequest[?, ?], _: ResponseMetadata) =>
          Some(
            HistogramCollectorConfig(
              DefaultResponseSizeHistogramName,
              buckets = HistogramCollectorConfig.DefaultSizeBuckets,
              unit = HistogramCollectorConfig.Bytes
            )
          ),
      spanName: GenericRequest[?, ?] => String = OpenTelemetryDefaults.spanName,
      requestAttributes: GenericRequest[?, ?] => Attributes = OpenTelemetryDefaults.requestAttributes,
      responseAttributes: (GenericRequest[?, ?], ResponseMetadata) => Attributes =
        OpenTelemetryDefaults.responseAttributes,
      errorAttributes: Throwable => Attributes = OpenTelemetryDefaults.errorAttributes
  ): OpenTelemetryMetricsConfig = usingMeter(
    openTelemetry
      .meterBuilder(OpenTelemetryDefaults.instrumentationScopeName)
      .setInstrumentationVersion(OpenTelemetryDefaults.instrumentationScopeVersion)
      .build(),
    clock,
    requestToLatencyHistogramMapper = requestToLatencyHistogramMapper,
    requestToInProgressCounterMapper = requestToInProgressCounterMapper,
    responseToSuccessCounterMapper = responseToSuccessCounterMapper,
    responseToErrorCounterMapper = responseToErrorCounterMapper,
    requestToFailureCounterMapper = requestToFailureCounterMapper,
    requestToSizeHistogramMapper = requestToSizeHistogramMapper,
    responseToSizeHistogramMapper = responseToSizeHistogramMapper,
    requestAttributes = requestAttributes,
    responseAttributes = responseAttributes,
    errorAttributes = errorAttributes
  )

  def usingMeter(
      meter: Meter,
      clock: Clock = Clock.systemUTC(),
      requestToLatencyHistogramMapper: GenericRequest[?, ?] => Option[HistogramCollectorConfig] =
        (_: GenericRequest[?, ?]) =>
          Some(
            HistogramCollectorConfig(
              DefaultLatencyHistogramName,
              buckets = HistogramCollectorConfig.DefaultLatencyBuckets,
              unit = HistogramCollectorConfig.Milliseconds
            )
          ),
      requestToInProgressCounterMapper: GenericRequest[?, ?] => Option[CollectorConfig] = (_: GenericRequest[?, ?]) =>
        Some(CollectorConfig(DefaultRequestsActiveCounterName)),
      responseToSuccessCounterMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[CollectorConfig] =
        (_: GenericRequest[?, ?], _: ResponseMetadata) => Some(CollectorConfig(DefaultSuccessCounterName)),
      responseToErrorCounterMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[CollectorConfig] =
        (_: GenericRequest[?, ?], _: ResponseMetadata) => Some(CollectorConfig(DefaultErrorCounterName)),
      requestToFailureCounterMapper: (GenericRequest[?, ?], Throwable) => Option[CollectorConfig] =
        (_: GenericRequest[?, ?], _: Throwable) => Some(CollectorConfig(DefaultFailureCounterName)),
      requestToSizeHistogramMapper: GenericRequest[?, ?] => Option[HistogramCollectorConfig] =
        (_: GenericRequest[?, ?]) =>
          Some(
            HistogramCollectorConfig(
              DefaultRequestSizeHistogramName,
              buckets = HistogramCollectorConfig.DefaultSizeBuckets,
              unit = HistogramCollectorConfig.Bytes
            )
          ),
      responseToSizeHistogramMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[HistogramCollectorConfig] =
        (_: GenericRequest[?, ?], _: ResponseMetadata) =>
          Some(
            HistogramCollectorConfig(
              DefaultResponseSizeHistogramName,
              buckets = HistogramCollectorConfig.DefaultSizeBuckets,
              unit = HistogramCollectorConfig.Bytes
            )
          ),
      requestAttributes: GenericRequest[?, ?] => Attributes = OpenTelemetryDefaults.requestAttributes,
      responseAttributes: (GenericRequest[?, ?], ResponseMetadata) => Attributes =
        OpenTelemetryDefaults.responseAttributes,
      errorAttributes: Throwable => Attributes = OpenTelemetryDefaults.errorAttributes
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
      responseToSizeHistogramMapper,
      requestAttributes,
      responseAttributes,
      errorAttributes
    )
}
