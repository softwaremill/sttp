package sttp.client

import java.io.ByteArrayInputStream

import sttp.client.testing.{ConvertToFuture, HttpTest}

class HttpURLConnectionBackendHttpTest extends HttpTest[Identity] {
  override implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend(
    customEncodingHandler = { case (stream, "custom") => new ByteArrayInputStream(customEncodedData.getBytes()) }
  )

  override implicit val backend: SttpBackend[Identity, Any] = HttpURLConnectionBackend()
  override implicit val convertToFuture: ConvertToFuture[Identity] = ConvertToFuture.id

  override def supportsCustomContentEncoding = true
}
