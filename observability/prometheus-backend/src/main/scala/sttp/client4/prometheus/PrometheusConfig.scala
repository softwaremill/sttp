package sttp.client4.prometheus

import io.prometheus.metrics.model.registry.PrometheusRegistry
import sttp.client4.GenericRequest
import sttp.client4.prometheus.PrometheusBackend._
import sttp.model.ResponseMetadata

final case class PrometheusConfig(
    requestToHistogramNameMapper: GenericRequest[?, ?] => Option[HistogramCollectorConfig] =
      (req: GenericRequest[?, ?]) => Some(addMethodLabel(HistogramCollectorConfig(DefaultHistogramName), req)),
    requestToInProgressGaugeNameMapper: GenericRequest[?, ?] => Option[CollectorConfig] = (req: GenericRequest[?, ?]) =>
      Some(addMethodLabel(CollectorConfig(DefaultRequestsActiveGaugeName), req)),
    responseToSuccessCounterMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[CollectorConfig] =
      (req: GenericRequest[?, ?], resp: ResponseMetadata) =>
        Some(addStatusLabel(addMethodLabel(CollectorConfig(DefaultSuccessCounterName), req), resp)),
    responseToErrorCounterMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[CollectorConfig] =
      (req: GenericRequest[?, ?], resp: ResponseMetadata) =>
        Some(addStatusLabel(addMethodLabel(CollectorConfig(DefaultErrorCounterName), req), resp)),
    requestToFailureCounterMapper: (GenericRequest[?, ?], Throwable) => Option[CollectorConfig] = (
        req: GenericRequest[?, ?],
        _: Throwable
    ) => Some(addMethodLabel(CollectorConfig(DefaultFailureCounterName), req)),
    requestToSizeSummaryMapper: GenericRequest[?, ?] => Option[CollectorConfig] = (req: GenericRequest[?, ?]) =>
      Some(addMethodLabel(CollectorConfig(DefaultRequestSizeName), req)),
    responseToSizeSummaryMapper: (GenericRequest[?, ?], ResponseMetadata) => Option[CollectorConfig] =
      (req: GenericRequest[?, ?], resp: ResponseMetadata) =>
        Some(addStatusLabel(addMethodLabel(CollectorConfig(DefaultResponseSizeName), req), resp)),
    prometheusRegistry: PrometheusRegistry = PrometheusRegistry.defaultRegistry
)

object PrometheusConfig {
  val Default: PrometheusConfig = PrometheusConfig()
}
