package sttp.client4.okhttp.monix

import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import okhttp3.OkHttpClient
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class OkHttpMonixCloseTest extends AnyFlatSpec with Matchers {
  "resourceUsingClient" should "shut down the client's dispatcher when released" in {
    val client = new OkHttpClient()

    OkHttpMonixBackend.resourceUsingClient(client).use(_ => Task.unit).runSyncUnsafe()

    client.dispatcher().executorService().isShutdown shouldBe true
  }
}
