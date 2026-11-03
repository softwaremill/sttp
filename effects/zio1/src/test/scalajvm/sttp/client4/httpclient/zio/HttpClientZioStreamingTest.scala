package sttp.client4.httpclient.zio

import sttp.capabilities.zio.ZioStreams
import sttp.client4.StreamBackend
import sttp.client4.impl.zio.ZioStreamingTest
import zio.Task

class HttpClientZioStreamingTest extends ZioStreamingTest {
  override val backend: StreamBackend[Task, ZioStreams] =
    runtime.unsafeRun(HttpClientZioBackend())

  override protected def supportsStreamingMultipartParts: Boolean = false

  // zio1's reactive-streams interop doesn't interrupt a stream waiting for data when its subscription is cancelled
  override protected def supportsCancellation: Boolean = false
}
