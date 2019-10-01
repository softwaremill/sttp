package sttp.client.asynchttpclient.zio

import org.scalatest.{FlatSpec, Matchers}
import org.scalatest.concurrent.ScalaFutures
import sttp.client._
import sttp.client.impl.zio.TaskMonadAsyncError
import sttp.client.testing.SttpBackendStub
import sttp.client.impl.zio._
import zio.Task

class SttpBackendStubZioTests extends FlatSpec with Matchers with ScalaFutures {

  "backend stub" should "cycle through responses using a single sent request" in {
    // given
    implicit val b: SttpBackendStub[Task, Nothing] = SttpBackendStub(TaskMonadAsyncError)
      .whenRequestMatches(_ => true)
      .thenRespondCyclic("a", "b", "c")

    // when
    val r = basicRequest.get(uri"http://example.org/a/b/c").send()

    // then
    runtime.unsafeRun(r).body shouldBe Right("a")
    runtime.unsafeRun(r).body shouldBe Right("b")
    runtime.unsafeRun(r).body shouldBe Right("c")
    runtime.unsafeRun(r).body shouldBe Right("a")
  }
}
