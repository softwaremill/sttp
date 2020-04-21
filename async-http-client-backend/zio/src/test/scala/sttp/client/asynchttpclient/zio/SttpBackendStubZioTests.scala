package sttp.client.asynchttpclient.zio

import org.scalatest.concurrent.ScalaFutures
import sttp.client._
import sttp.client.impl.zio.RIOMonadAsyncError
import sttp.client.testing.SttpBackendStub
import sttp.client.impl.zio._
import zio.Task
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class SttpBackendStubZioTests extends AnyFlatSpec with Matchers with ScalaFutures {

  "backend stub" should "cycle through responses using a single sent request" in {
    // given
    implicit val b: SttpBackendStub[Task, Nothing] = SttpBackendStub(new RIOMonadAsyncError[Any])
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
