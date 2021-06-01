package sttp.client3.httpclient.zio

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sttp.client3._
import sttp.client3.impl.zio._
import sttp.client3.testing.SttpBackendStub
import sttp.model.Method
import zio.{Task, ZIO}
import zio.stream.ZStream

class SttpBackendStubZioTests extends AnyFlatSpec with Matchers with ScalaFutures with ZioTestBase {

  "backend stub" should "cycle through responses using a single sent request" in {
    // given
    val backend: SttpBackendStub[Task, Any] = SttpBackendStub(new RIOMonadAsyncError[Any])
      .whenRequestMatches(_ => true)
      .thenRespondCyclic("a", "b", "c")
    // when
    val r = basicRequest.get(uri"http://example.org/a/b/c").send(backend)

    // then
    runtime.unsafeRun(r).body shouldBe Right("a")
    runtime.unsafeRun(r).body shouldBe Right("b")
    runtime.unsafeRun(r).body shouldBe Right("c")
    runtime.unsafeRun(r).body shouldBe Right("a")
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

    runtime.unsafeRun(effect) should contain theSameElementsAs ((1 to 33).flatMap(_ => Seq("a", "b", "c")) ++ Seq("a"))
      .map(Right(_))
  }

  it should "allow effectful stubbing" in {
    import stubbing._
    val r1 = send(basicRequest.get(uri"http://example.org/a")).map(_.body)
    val r2 = send(basicRequest.post(uri"http://example.org/a/b")).map(_.body)
    val r3 = send(basicRequest.get(uri"http://example.org/a/b/c")).map(_.body)

    val effect = for {
      _ <- whenRequestMatches(_.uri.toString.endsWith("c")).thenRespond("c")
      _ <- whenRequestMatchesPartial { case r if r.method == Method.POST => Response.ok("b") }
      _ <- whenAnyRequest.thenRespond("a")
      resp <- r1 <&> r2 <&> r3
    } yield resp

    runtime.unsafeRun(effect.provideCustomLayer(HttpClientZioBackend.stubLayer)) shouldBe
      (((Right("a"), Right("b")), Right("c")))
  }

  it should "allow effectful cyclical stubbing" in {
    import stubbing._
    val r = basicRequest.get(uri"http://example.org/a/b/c")

    val effect = (for {
      _ <- whenAnyRequest.thenRespondCyclic("a", "b", "c")
      resp <- ZStream.repeatEffect(send(r)).take(4).runCollect
    } yield resp).provideCustomLayer(HttpClientZioBackend.stubLayer)

    runtime.unsafeRun(effect).map(_.body).toList shouldBe List(Right("a"), Right("b"), Right("c"), Right("a"))
  }
}
