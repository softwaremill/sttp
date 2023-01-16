package sttp.client3.prometheus

import io.prometheus.client.CollectorRegistry
import sttp.client3.AbstractRequest
import sttp.client3.Response
import sttp.client3.prometheus.PrometheusBackend._

final case class PrometheusConfig(
    requestToHistogramNameMapper: AbstractRequest[_, _] => Option[HistogramCollectorConfig] =
      (req: AbstractRequest[_, _]) => Some(addMethodLabel(HistogramCollectorConfig(DefaultHistogramName), req)),
    requestToInProgressGaugeNameMapper: AbstractRequest[_, _] => Option[CollectorConfig] =
      (req: AbstractRequest[_, _]) => Some(addMethodLabel(CollectorConfig(DefaultRequestsInProgressGaugeName), req)),
    responseToSuccessCounterMapper: (AbstractRequest[_, _], Response[_]) => Option[CollectorConfig] =
      (req: AbstractRequest[_, _], resp: Response[_]) =>
        Some(addStatusLabel(addMethodLabel(CollectorConfig(DefaultSuccessCounterName), req), resp)),
    responseToErrorCounterMapper: (AbstractRequest[_, _], Response[_]) => Option[CollectorConfig] =
      (req: AbstractRequest[_, _], resp: Response[_]) =>
        Some(addStatusLabel(addMethodLabel(CollectorConfig(DefaultErrorCounterName), req), resp)),
    requestToFailureCounterMapper: (AbstractRequest[_, _], Throwable) => Option[CollectorConfig] =
      (req: AbstractRequest[_, _], _: Throwable) =>
        Some(addMethodLabel(CollectorConfig(DefaultFailureCounterName), req)),
    requestToSizeSummaryMapper: AbstractRequest[_, _] => Option[CollectorConfig] = (req: AbstractRequest[_, _]) =>
      Some(addMethodLabel(CollectorConfig(DefaultRequestSizeName), req)),
    responseToSizeSummaryMapper: (AbstractRequest[_, _], Response[_]) => Option[CollectorConfig] =
      (req: AbstractRequest[_, _], resp: Response[_]) =>
        Some(addStatusLabel(addMethodLabel(CollectorConfig(DefaultResponseSizeName), req), resp)),
    collectorRegistry: CollectorRegistry = CollectorRegistry.defaultRegistry
)

object PrometheusConfig {
  val Default = PrometheusConfig()
}
