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
    requestToLatencyHistogramMapper: GenericRequest[_, _] => Option[HistogramCollectorConfig],
    requestToInProgressCounterMapper: GenericRequest[_, _] => Option[CollectorConfig],
    responseToSuccessCounterMapper: (GenericRequest[_, _], ResponseMetadata) => Option[CollectorConfig],
    requestToErrorCounterMapper: (GenericRequest[_, _], ResponseMetadata) => Option[CollectorConfig],
    requestToFailureCounterMapper: (GenericRequest[_, _], Throwable) => Option[CollectorConfig],
    requestToSizeHistogramMapper: GenericRequest[_, _] => Option[HistogramCollectorConfig],
    responseToSizeHistogramMapper: (GenericRequest[_, _], ResponseMetadata) => Option[HistogramCollectorConfig],
    requestAttributes: GenericRequest[_, _] => Attributes,
    responseAttributes: (GenericRequest[_, _], ResponseMetadata) => Attributes,
    errorAttributes: Throwable => Attributes
)

object OpenTelemetryMetricsConfig {
  def apply(
      openTelemetry: OpenTelemetry,
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
      responseToSuccessCounterMapper: (GenericRequest[_, _], ResponseMetadata) => Option[CollectorConfig] =
        (_: GenericRequest[_, _], _: ResponseMetadata) => Some(CollectorConfig(DefaultSuccessCounterName)),
      responseToErrorCounterMapper: (GenericRequest[_, _], ResponseMetadata) => Option[CollectorConfig] =
        (_: GenericRequest[_, _], _: ResponseMetadata) => Some(CollectorConfig(DefaultErrorCounterName)),
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
      responseToSizeHistogramMapper: (GenericRequest[_, _], ResponseMetadata) => Option[HistogramCollectorConfig] =
        (_: GenericRequest[_, _], _: ResponseMetadata) =>
          Some(
            HistogramCollectorConfig(
              DefaultResponseSizeHistogramName,
              buckets = HistogramCollectorConfig.DefaultSizeBuckets,
              unit = HistogramCollectorConfig.Bytes
            )
          ),
      spanName: GenericRequest[_, _] => String = OpenTelemetryDefaults.spanName _,
      requestAttributes: GenericRequest[_, _] => Attributes = OpenTelemetryDefaults.requestAttributes _,
      responseAttributes: (GenericRequest[_, _], ResponseMetadata) => Attributes =
        OpenTelemetryDefaults.responseAttributes _,
      errorAttributes: Throwable => Attributes = OpenTelemetryDefaults.errorAttributes _
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
      responseToSuccessCounterMapper: (GenericRequest[_, _], ResponseMetadata) => Option[CollectorConfig] =
        (_: GenericRequest[_, _], _: ResponseMetadata) => Some(CollectorConfig(DefaultSuccessCounterName)),
      responseToErrorCounterMapper: (GenericRequest[_, _], ResponseMetadata) => Option[CollectorConfig] =
        (_: GenericRequest[_, _], _: ResponseMetadata) => Some(CollectorConfig(DefaultErrorCounterName)),
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
      responseToSizeHistogramMapper: (GenericRequest[_, _], ResponseMetadata) => Option[HistogramCollectorConfig] =
        (_: GenericRequest[_, _], _: ResponseMetadata) =>
          Some(
            HistogramCollectorConfig(
              DefaultResponseSizeHistogramName,
              buckets = HistogramCollectorConfig.DefaultSizeBuckets,
              unit = HistogramCollectorConfig.Bytes
            )
          ),
      requestAttributes: GenericRequest[_, _] => Attributes = OpenTelemetryDefaults.requestAttributes _,
      responseAttributes: (GenericRequest[_, _], ResponseMetadata) => Attributes =
        OpenTelemetryDefaults.responseAttributes _,
      errorAttributes: Throwable => Attributes = OpenTelemetryDefaults.errorAttributes _
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
