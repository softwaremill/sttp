package sttp.client4

import sttp.client4.httpurlconnection.HttpURLConnectionBackend
import sttp.client4.testing.{ConvertToFuture, HttpTest}
import sttp.client4.testing.HttpTest.endpoint
import sttp.shared.Identity

import java.io.ByteArrayInputStream
import sttp.client4.compression.Decompressor
import java.io.InputStream

class HttpURLConnectionBackendHttpTest extends HttpTest[Identity] {
  override val backend: SyncBackend = HttpURLConnectionBackend(
    compressionHandlers =
      HttpURLConnectionBackend.DefaultCompressionHandlers.addDecompressor(new Decompressor[InputStream] {
        override val encoding: String = "custom"
        override def apply(inputStream: InputStream): InputStream =
          new ByteArrayInputStream(customEncodedData.getBytes())

      })
  )
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id

  override def supportsCustomContentEncoding = true
  override def supportsHostHeaderOverride = false
  override def supportsCancellation = false
  override def supportsDeflateWrapperChecking = false
  override def supportsQuery = false

  // https://github.com/softwaremill/sttp/issues/3044
  "send all values of a repeated request header" in {
    basicRequest
      .get(uri"$endpoint/echo/headers")
      .header("X-Req", "one")
      .header("X-Req", "two", DuplicateHeaderBehavior.Add)
      .response(asStringAlways)
      .send(backend)
      .body
      .split(",")
      .toList should contain allOf ("X-Req->one", "X-Req->two")
  }

  override def timeoutToNone[T](t: Identity[T], timeoutMillis: Int): Identity[Option[T]] = Some(t)
}
