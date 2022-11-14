package sttp.client3.httpclient.zio

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.capabilities.Streams
import sttp.client3._
import sttp.client3.impl.zio._
import sttp.client3.testing.SttpBackendStub
import zio.{Task, ZIO}

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

  it should "lift errors due to mapping with impure functions into the response monad" in {
    val backend: SttpBackendStub[Task, Any] =
      SttpBackendStub(new RIOMonadAsyncError[Any]).whenAnyRequest.thenRespondOk()

    val error = new IllegalStateException("boom")

    val r = basicRequest
      .post(uri"http://example.org")
      .response(asStringAlways.map[Int](_ => throw error))
      .send(backend)

    unsafeRunSyncOrThrow(r.either) match {
      case Left(_: IllegalStateException) => succeed
      case _                              => fail(s"Should be a failure: $r")
    }
  }

  trait TestStreams extends Streams[TestStreams] {
    override type BinaryStream = List[Byte]
    override type Pipe[A, B] = A => B
  }

  object TestStreams extends TestStreams

  it should "lift errors due to mapping stream with impure functions into the response monad" in {
    val backend = SttpBackendStub[Task, TestStreams](new RIOMonadAsyncError[Any]).whenAnyRequest
      .thenRespond(SttpBackendStub.RawStream(List(1: Byte)))

    val error = new IllegalStateException("boom")

    val r = basicRequest
      .get(uri"http://example.org")
      .response(asStreamAlways[Task, Int, TestStreams](TestStreams)(_ => throw error))
      .send(backend)

    unsafeRunSyncOrThrow(r.either) match {
      case Left(_: IllegalStateException) => succeed
      case _                              => fail(s"Should be a failure: $r")
    }
  }
}
