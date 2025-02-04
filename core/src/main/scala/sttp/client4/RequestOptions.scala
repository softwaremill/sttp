package sttp.client4

import scala.concurrent.duration.Duration
import sttp.model.HttpVersion
import sttp.client4.logging.LoggingOptions
import sttp.model.ResponseMetadata

/** Options for a [[Request]]. The defaults can be found on [[emptyRequest]].
  *
  * @param redirectToGet
  *   When a POST or PUT request is redirected, should the redirect be a POST/PUT as well (with the original body), or
  *   should the request be converted to a GET without a body. Note that this only affects 301 and 302 redirects. 303
  *   redirects are always converted, while 307 and 308 redirects always keep the same method. See
  *   https://developer.mozilla.org/en-US/docs/Web/HTTP/Redirections for details.
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
  * @param maxResponseBodyLength
  *   The maximum length of the response body (in bytes). When sending the request, if the response body is longer, an
  *   exception is thrown / a failed effect is returned. By default, when `None`, the is no limit on the response body's
  *   length.
  * @param onBodyReceived
  *   A callback invoked when the entire response body has been received & decompressed (but not yet fully handled, e.g.
  *   by parsing the received data). This is used by logging & metrics backends to properly capture timing information.
  *   The callback is not called when there's an exception while reading the response body, decompressing, or for
  *   WebSocket requests.
  */
case class RequestOptions(
    followRedirects: Boolean,
    readTimeout: Duration,
    maxRedirects: Int,
    redirectToGet: Boolean,
    decompressResponseBody: Boolean,
    compressRequestBody: Option[String],
    httpVersion: Option[HttpVersion],
    loggingOptions: LoggingOptions,
    maxResponseBodyLength: Option[Long],
    onBodyReceived: ResponseMetadata => Unit
)
