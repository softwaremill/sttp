package sttp.client3

import java.io.ByteArrayInputStream

import sttp.client3.testing.{ConvertToFuture, HttpTest}

class HttpURLConnectionBackendHttpTest extends HttpTest[Identity] {
  override val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend(
    customEncodingHandler = { case (_, "custom") => new ByteArrayInputStream(customEncodedData.getBytes()) }
  )
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id

  override def supportsCustomContentEncoding = true
  override def supportsHostHeaderOverride = false
  override def supportsCancellation: Boolean = false

  override def timeoutToNone[T](t: Identity[T], timeoutMillis: Int): Identity[Option[T]] = Some(t)
}
