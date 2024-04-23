package sttp.client4

import sttp.client4.internal.ContentEncoding

import scala.concurrent.duration.Duration

case class RequestOptions(
    followRedirects: Boolean,
    readTimeout: Duration, // TODO: Use FiniteDuration while migrating to sttp-4
    maxRedirects: Int,
    redirectToGet: Boolean,
    encoding: List[ContentEncoding] = List.empty
)
