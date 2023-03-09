package sttp.client3.prometheus

import io.prometheus.client.CollectorRegistry
import sttp.client3.GenericRequest
import sttp.client3.Response
import sttp.client3.prometheus.PrometheusBackend._

final case class PrometheusConfig(
                                   requestToHistogramNameMapper: GenericRequest[_, _] => Option[HistogramCollectorConfig] =
      (req: GenericRequest[_, _]) => Some(addMethodLabel(HistogramCollectorConfig(DefaultHistogramName), req)),
                                   requestToInProgressGaugeNameMapper: GenericRequest[_, _] => Option[CollectorConfig] =
      (req: GenericRequest[_, _]) => Some(addMethodLabel(CollectorConfig(DefaultRequestsInProgressGaugeName), req)),
                                   responseToSuccessCounterMapper: (GenericRequest[_, _], Response[_]) => Option[CollectorConfig] =
      (req: GenericRequest[_, _], resp: Response[_]) =>
        Some(addStatusLabel(addMethodLabel(CollectorConfig(DefaultSuccessCounterName), req), resp)),
                                   responseToErrorCounterMapper: (GenericRequest[_, _], Response[_]) => Option[CollectorConfig] =
      (req: GenericRequest[_, _], resp: Response[_]) =>
        Some(addStatusLabel(addMethodLabel(CollectorConfig(DefaultErrorCounterName), req), resp)),
                                   requestToFailureCounterMapper: (GenericRequest[_, _], Throwable) => Option[CollectorConfig] =
      (req: GenericRequest[_, _], _: Throwable) =>
        Some(addMethodLabel(CollectorConfig(DefaultFailureCounterName), req)),
                                   requestToSizeSummaryMapper: GenericRequest[_, _] => Option[CollectorConfig] = (req: GenericRequest[_, _]) =>
      Some(addMethodLabel(CollectorConfig(DefaultRequestSizeName), req)),
                                   responseToSizeSummaryMapper: (GenericRequest[_, _], Response[_]) => Option[CollectorConfig] =
      (req: GenericRequest[_, _], resp: Response[_]) =>
        Some(addStatusLabel(addMethodLabel(CollectorConfig(DefaultResponseSizeName), req), resp)),
                                   collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
)

object PrometheusConfig {
  val Default: PrometheusConfig = PrometheusConfig()
}
