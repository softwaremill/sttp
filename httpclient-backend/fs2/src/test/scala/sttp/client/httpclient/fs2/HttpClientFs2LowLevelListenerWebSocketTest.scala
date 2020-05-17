package sttp.client.httpclient.fs2

import cats.effect.IO
import sttp.client.httpclient.HttpClientLowLevelListenerWebSocketTest

class HttpClientFs2LowLevelListenerWebSocketTest
    extends HttpClientLowLevelListenerWebSocketTest[IO]
    with HttpClientFs2TestBase
