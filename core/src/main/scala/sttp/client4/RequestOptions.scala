package sttp.client4

import scala.concurrent.duration.Duration
import sttp.model.HttpVersion
import sttp.client4.logging.LoggingOptions

/** Options for a [[Request]]. The defaults can be found on [[emptyRequest]]. */
case class RequestOptions(
    followRedirects: Boolean,
    readTimeout: Duration,
    maxRedirects: Int,
    redirectToGet: Boolean,
    decompressResponseBody: Boolean,
    compressRequestBody: Option[String],
    httpVersion: Option[HttpVersion],
    loggingOptions: LoggingOptions
)
