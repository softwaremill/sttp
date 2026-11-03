package sttp.client4.impl.zio

import sttp.capabilities.zio.ZioStreams
import sttp.client4.StreamBackend
import zio.Task

class FetchZioStreamingTest extends ZioStreamingTest {
  override val backend: StreamBackend[Task, ZioStreams] = FetchZioBackend()

  override protected def supportsStreamingMultipartParts: Boolean = false
}
