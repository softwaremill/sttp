package sttp.client4.armeria.monix

import com.linecorp.armeria.client.{ClientFactory, WebClient}
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ArmeriaMonixCloseTest extends AnyFlatSpec with Matchers {
  "resourceUsingClient" should "close the client's factory when released" in {
    val factory = ClientFactory.builder().build()
    val client = WebClient.builder().factory(factory).build()

    ArmeriaMonixBackend.resourceUsingClient(client).use(_ => Task.unit).runSyncUnsafe()

    factory.isClosed shouldBe true
  }
}
