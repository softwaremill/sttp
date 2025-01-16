package sttp.client4

import scala.concurrent.duration.Duration
import sttp.model.HttpVersion
import sttp.client4.logging.LoggingOptions

/** Options for a [[Request]]. The defaults can be found on [[emptyRequest]].
  *
  * @param decompressResponseBody
  *   Should the response body be decompressed, if a `Content-Encoding` header is present. By default, backends support
  *   [[sttp.model.Encodings.Gzip]] and [[sttp.model.Encodings.Deflate]] encodings, but others might available as well;
  *   refer to the backend documentation for details. If an encoding is not supported, an exception is thrown / a failed
  *   effect returned, when sending the request.
  * @param compressRequestBody
  *   Should the request body be compressed, and if so, with which encoding. By default, backends support
  *   [[sttp.model.Encodings.Gzip]] and [[sttp.model.Encodings.Deflate]] encodings, but others might available as well;
  *   refer to the backend documentation for details. If an encoding is not supported, an exception is thrown / a failed
  *   effect returned, when sending the request.
  */
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
