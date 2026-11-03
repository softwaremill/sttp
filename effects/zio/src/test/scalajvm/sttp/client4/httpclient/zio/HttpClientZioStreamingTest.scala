package sttp.client4.httpclient.zio

import sttp.capabilities.zio.ZioStreams
import sttp.client4.StreamBackend
import sttp.client4.impl.zio.ZioStreamingTest
import zio.Task

class HttpClientZioStreamingTest extends ZioStreamingTest {
  override val backend: StreamBackend[Task, ZioStreams] =
    unsafeRunSyncOrThrow(HttpClientZioBackend())

  override protected def supportsStreamingMultipartParts: Boolean = false
}
