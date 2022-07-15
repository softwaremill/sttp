package sttp.client3.asynchttpclient.zio

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3._
import sttp.client3.impl.zio._
import sttp.client3.testing.SttpBackendStub
import zio._

class SttpBackendStubZioTests extends AnyFlatSpec with Matchers with ScalaFutures with ZioTestBase {

  "backend stub" should "cycle through responses using a single sent request" in {
    // given
    val backend: SttpBackendStub[Task, Any] = SttpBackendStub(new RIOMonadAsyncError[Any])
      .whenRequestMatches(_ => true)
      .thenRespondCyclic("a", "b", "c")
    // when
    val r = basicRequest.get(uri"http://example.org/a/b/c").send(backend)

    // then
    unsafeRunSyncOrThrow(r).body shouldBe Right("a")
    unsafeRunSyncOrThrow(r).body shouldBe Right("b")
    unsafeRunSyncOrThrow(r).body shouldBe Right("c")
    unsafeRunSyncOrThrow(r).body shouldBe Right("a")
  }

  it should "cycle through responses when called concurrently" in {
    // given
    val backend: SttpBackendStub[Task, Any] = SttpBackendStub(new RIOMonadAsyncError[Any])
      .whenRequestMatches(_ => true)
      .thenRespondCyclic("a", "b", "c")

    // when
    val r = basicRequest.get(uri"http://example.org/a/b/c").send(backend)

    // then
    val effect = ZIO
      .collectAllPar(Seq.fill(100)(r))
      .map(_.map(_.body))

    unsafeRunSyncOrThrow(effect) should contain theSameElementsAs ((1 to 33).flatMap(_ => Seq("a", "b", "c")) ++ Seq(
      "a"
    ))
      .map(Right(_))
  }
}
