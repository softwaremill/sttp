package sttp.client4.prometheus

import io.prometheus.metrics.model.registry.PrometheusRegistry
import sttp.client4.GenericRequest
import sttp.client4.Response
import sttp.client4.prometheus.PrometheusBackend._

final case class PrometheusConfig(
                                   requestToHistogramNameMapper: GenericRequest[_, _] => Option[HistogramCollectorConfig] =
      (req: GenericRequest[_, _]) => Some(addMethodLabel(HistogramCollectorConfig(DefaultHistogramName), req)),
                                   requestToInProgressGaugeNameMapper: GenericRequest[_, _] => Option[CollectorConfig] = (req: GenericRequest[_, _]) =>
      Some(addMethodLabel(CollectorConfig(DefaultRequestsActiveGaugeName), req)),
                                   responseToSuccessCounterMapper: (GenericRequest[_, _], Response[_]) => Option[CollectorConfig] =
      (req: GenericRequest[_, _], resp: Response[_]) =>
        Some(addStatusLabel(addMethodLabel(CollectorConfig(DefaultSuccessCounterName), req), resp)),
                                   responseToErrorCounterMapper: (GenericRequest[_, _], Response[_]) => Option[CollectorConfig] =
      (req: GenericRequest[_, _], resp: Response[_]) =>
        Some(addStatusLabel(addMethodLabel(CollectorConfig(DefaultErrorCounterName), req), resp)),
                                   requestToFailureCounterMapper: (GenericRequest[_, _], Throwable) => Option[CollectorConfig] = (
        req: GenericRequest[_, _],
        _: Throwable
    ) => Some(addMethodLabel(CollectorConfig(DefaultFailureCounterName), req)),
                                   requestToSizeSummaryMapper: GenericRequest[_, _] => Option[CollectorConfig] = (req: GenericRequest[_, _]) =>
      Some(addMethodLabel(CollectorConfig(DefaultRequestSizeName), req)),
                                   responseToSizeSummaryMapper: (GenericRequest[_, _], Response[_]) => Option[CollectorConfig] =
      (req: GenericRequest[_, _], resp: Response[_]) =>
        Some(addStatusLabel(addMethodLabel(CollectorConfig(DefaultResponseSizeName), req), resp)),
                                   prometheusRegistry: PrometheusRegistry = PrometheusRegistry.defaultRegistry
)

object PrometheusConfig {
  val Default: PrometheusConfig = PrometheusConfig()
}
