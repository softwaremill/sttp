package sttp.client3.httpclient.fs2

import sttp.client3.impl.fs2.Fs2StreamingTest

class HttpClientFs2StreamingTest extends Fs2StreamingTest with HttpClientFs2TestBase {
  override protected def supportsStreamingMultipartParts: Boolean = false
}
