package sttp.client4.opentelemetry.otel4s

import org.typelevel.otel4s.metrics.BucketBoundaries
import sttp.model.Uri

final case class Otel4sMetricsConfig(
    requestDurationHistogramBuckets: BucketBoundaries,
    requestBodySizeHistogramBuckets: Option[BucketBoundaries],
    responseBodySizeHistogramBuckets: Option[BucketBoundaries],
    urlTemplate: Uri => Option[String] = (_) => None
)

object Otel4sMetricsConfig {
  val DefaultDurationBuckets: BucketBoundaries = BucketBoundaries(
    0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1, 2.5, 5, 7.5, 10
  )

  val default: Otel4sMetricsConfig = Otel4sMetricsConfig(
    requestDurationHistogramBuckets = DefaultDurationBuckets,
    requestBodySizeHistogramBuckets = None,
    responseBodySizeHistogramBuckets = None,
    urlTemplate = (_) => None
  )
}
