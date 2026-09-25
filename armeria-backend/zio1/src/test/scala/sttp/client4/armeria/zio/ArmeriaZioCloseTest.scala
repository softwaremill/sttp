package sttp.client4.armeria.zio

import com.linecorp.armeria.client.{ClientFactory, WebClient}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client4.impl.zio.ZioTestBase
import zio.ZIO

class ArmeriaZioCloseTest extends AnyFlatSpec with Matchers with ZioTestBase {
  "managedUsingClient" should "close the client's factory when released" in {
    val factory = ClientFactory.builder().build()
    val client = WebClient.builder().factory(factory).build()

    runtime.unsafeRun(ArmeriaZioBackend.managedUsingClient(client).use_(ZIO.unit))

    factory.isClosed shouldBe true
  }
}
