package sttp.client4

import scala.concurrent.duration.Duration
import sttp.model.HttpVersion

case class RequestOptions(
    followRedirects: Boolean,
    readTimeout: Duration, // TODO: Use FiniteDuration while migrating to sttp-4
    maxRedirects: Int,
    redirectToGet: Boolean,
    disableAutoDecompression: Boolean,
    httpVersion: Option[HttpVersion]
)
