package sttp.client4.httpclient.zio

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.impl.zio.ZioTestBase
import zio.ZIO

import java.net.http.HttpClient
import java.util.concurrent.Executors

class HttpClientZioCloseTest extends AnyFlatSpec with Matchers with ZioTestBase {
  "layerUsingClient" should "shut down the client's executor when released" in {
    val executor = Executors.newFixedThreadPool(1)
    val client = HttpClient.newBuilder().executor(executor).build()

    runtime.unsafeRun(HttpClientZioBackend.layerUsingClient(client).build.use_(ZIO.unit))

    executor.isShutdown shouldBe true
  }
}
