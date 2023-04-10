package sttp.client4

import scala.concurrent.duration.Duration

case class RequestOptions(
    followRedirects: Boolean,
    readTimeout: Duration, // TODO: Use FiniteDuration while migrating to sttp-4
    maxRedirects: Int,
    redirectToGet: Boolean
)
